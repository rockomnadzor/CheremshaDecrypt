#include <jni.h>
#include <unicorn/unicorn.h>
#include <android/log.h>
#include <cstring>
#include <cstdint>
#include <vector>
#include <map>
#include <string>
#include <functional>

#define TAG "UnicornBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---- memory layout (совпадает с emu_core.js) ----
static const uint64_t BASE       = 0x100000;
static const uint64_t HOOKBASE   = 0x40000000;
static const uint64_t HOOK_STOP  = HOOKBASE + 0x800;
static const uint64_t STACK_BASE = 0x70000000;
static const uint64_t STACK_SIZE = 4ull * 1024 * 1024;
static const uint64_t HEAP_BASE  = 0x100000000ull;
static const uint64_t HEAP_SIZE  = 64ull * 1024 * 1024;
static const uint64_t MMAP_BASE  = 0x200000000ull;
static const uint64_t MMAP_SIZE  = 32ull * 1024 * 1024;
static const uint64_t TLS_BASE   = 0x300000000ull;
static const uint64_t TLS_SIZE   = 64 * 1024;

static const uint64_t H_CLASS  = 0x900001;
static const uint64_t H_MID    = 0x900002;
static const uint64_t H_INARR  = 0x900004;
static const uint64_t H_OUTARR = 0x900005;

static int RX(int i) {
    if (i <= 28) return UC_ARM64_REG_X0 + i;
    if (i == 29) return UC_ARM64_REG_X29;
    return UC_ARM64_REG_X30;
}

class Emulator {
public:
    std::string diagLog;
    uint64_t lastFetchAddr = 0;
    uint64_t lastPcBeforeFetch = 0;
    uint64_t traceRing[16] = {0};
    int traceIdx = 0;
    uc_engine* uc = nullptr;
    JNIEnv* realEnv = nullptr;

    std::vector<uint8_t> soBytes;
    std::vector<uint8_t> inBytes;
    std::vector<uint8_t> out;
    uint64_t outLen = 0;
    bool haveOut = false;

    uint64_t inArrGuest = 0;
    uint64_t heapPtr = HEAP_BASE;
    uint64_t mmapPtr = MMAP_BASE;
    std::map<uint64_t, uint64_t> allocSizes;
    uint64_t errnoLoc = 0;
    std::string lastNewStr;
    int64_t redirect = -1;
    uint32_t rng = 0x12345678;

    std::vector<std::function<void()>> handlers;

    Emulator() { out.resize(1 << 20); }

    // ---- register helpers ----
    uint64_t regGet(int id) {
        uint64_t v = 0;
        uc_reg_read(uc, id, &v);
        return v;
    }
    void regSet(int id, uint64_t v) {
        uc_reg_write(uc, id, &v);
    }
    uint64_t A(int i) { return regGet(RX(i)); }
    void RET(uint64_t v) { regSet(RX(0), v); }

    // ---- guest memory helpers ----
    void gwrite(uint64_t addr, const uint8_t* data, size_t n) {
        uc_mem_write(uc, addr, data, n);
    }
    void gwrite(uint64_t addr, const std::vector<uint8_t>& data) {
        if (!data.empty()) uc_mem_write(uc, addr, data.data(), data.size());
    }
    std::vector<uint8_t> gread(uint64_t addr, size_t n) {
        std::vector<uint8_t> buf(n);
        if (n) uc_mem_read(uc, addr, buf.data(), n);
        return buf;
    }
    uint32_t gread32(uint64_t addr) {
        auto b = gread(addr, 4);
        return b[0] | (b[1] << 8) | (b[2] << 16) | (b[3] << 24);
    }
    uint64_t gread64(uint64_t addr) {
        auto b = gread(addr, 8);
        uint64_t v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | b[i];
        return v;
    }
    void gwrite64(uint64_t addr, uint64_t v) {
        uint8_t b[8];
        for (int i = 0; i < 8; i++) { b[i] = v & 0xff; v >>= 8; }
        gwrite(addr, b, 8);
    }
    std::vector<uint8_t> greadCStr(uint64_t addr) {
        std::vector<uint8_t> out2;
        for (;;) {
            auto chunk = gread(addr + out2.size(), 64);
            for (int i = 0; i < 64; i++) {
                if (chunk[i] == 0) return out2;
                out2.push_back(chunk[i]);
            }
        }
    }
    std::string greadStr(uint64_t addr) {
        auto b = greadCStr(addr);
        return std::string((char*)b.data(), b.size());
    }

    // ---- allocator ----
    uint64_t gmalloc(uint64_t n) {
        if (n == 0) n = 1;
        uint64_t p = (heapPtr + 15) & ~15ull;
        allocSizes[p] = n;
        heapPtr = p + n;
        return p;
    }
    uint64_t gsize(uint64_t p) {
        auto it = allocSizes.find(p);
        return it != allocSizes.end() ? it->second : 0;
    }
    uint64_t gmmap(uint64_t n) {
        uint64_t p = (mmapPtr + 4095) & ~4095ull;
        mmapPtr = p + n;
        return p;
    }

    uint64_t mkJString(const std::string& s) {
        uint64_t p = gmalloc(s.size() + 8);
        gwrite64(p, s.size());
        gwrite(p + 4, (const uint8_t*)s.data(), s.size());
        uint8_t z = 0;
        gwrite(p + 4 + s.size(), &z, 1);
        return p;
    }

    uint32_t rnd() {
        rng = (uint32_t)(rng * 1103515245u + 12345u);
        return rng;
    }

    // ---- реальный JNI-callback: marker -> RSA-ключ через EncryptedSubUrlHelper.getHelp ----
    std::string getHelp(const std::string& markerIn) {
        if (!realEnv) return "";
        jclass cls = realEnv->FindClass("su/happ/proxyutility/util/protection/EncryptedSubUrlHelper");
        if (!cls) { realEnv->ExceptionClear(); return ""; }
        jmethodID mid = realEnv->GetStaticMethodID(cls, "getHelp", "(Ljava/lang/String;)Ljava/lang/String;");
        if (!mid) { realEnv->ExceptionClear(); return ""; }
        jstring jMarker = realEnv->NewStringUTF(markerIn.c_str());
        jstring jResult = (jstring)realEnv->CallStaticObjectMethod(cls, mid, jMarker);
        std::string result;
        if (jResult) {
            const char* chars = realEnv->GetStringUTFChars(jResult, nullptr);
            if (chars) {
                result = chars;
                realEnv->ReleaseStringUTFChars(jResult, chars);
            }
            realEnv->DeleteLocalRef(jResult);
        }
        realEnv->DeleteLocalRef(jMarker);
        realEnv->DeleteLocalRef(cls);
        return result;
    }

    // ---- регистрация хука-обработчика в странице HOOKBASE ----
    uint64_t regHook(std::function<void()> fn) {
        uint64_t idx = handlers.size();
        handlers.push_back(fn);
        return HOOKBASE + idx * 4;
    }

    uint64_t resolveImport(const std::string& name);
    void setupLibcHandlers();
    void setupJniHandlers(uint64_t table, uint64_t /*unused*/);
    bool loadElfAndRun(const std::string& entrySymbol);
};

// ---- диспетчер хука: вызывается при попадании PC в диапазон HOOKBASE ----
static void hookCodeTrampoline(uc_engine* uc, uint64_t address, uint32_t size, void* user_data) {
    auto* emu = (Emulator*)user_data;
    if (address >= HOOKBASE && address < HOOKBASE + 0x800) {
        uint64_t idx = (address - HOOKBASE) / 4;
        if (idx < emu->handlers.size()) {
            emu->redirect = -1;
            emu->handlers[idx]();
            uint64_t tgt = emu->redirect >= 0 ? (uint64_t)emu->redirect : emu->regGet(UC_ARM64_REG_X30);
            emu->redirect = -1;
            emu->regSet(UC_ARM64_REG_PC, tgt);
        }
    }
}

void Emulator::setupLibcHandlers() {
    // регистрация ниже через resolveImport по имени
}

uint64_t Emulator::resolveImport(const std::string& name) {
    auto stub0 = [this]() { RET(0); };

    if (name == "malloc") return regHook([this]{ RET(gmalloc(A(0))); });
    if (name == "calloc") return regHook([this]{
        uint64_t n = A(0) * A(1); uint64_t p = gmalloc(n);
        std::vector<uint8_t> z(n, 0); gwrite(p, z); RET(p);
    });
    if (name == "realloc") return regHook([this]{
        uint64_t o = A(0), n = A(1);
        if (!o) { RET(gmalloc(n)); return; }
        uint64_t os = gsize(o), p = gmalloc(n);
        gwrite(p, gread(o, std::min(os, n))); RET(p);
    });
    if (name == "free") return regHook([this]{ RET(0); });
    if (name == "posix_memalign") return regHook([this]{
        uint64_t pp = A(0), al = A(1), n = A(2);
        uint64_t p = gmalloc(n + al); p = (p + al - 1) & ~(al - 1);
        gwrite64(pp, p); RET(0);
    });
    if (name == "memcpy" || name == "memmove") return regHook([this]{
        uint64_t d = A(0), s = A(1), n = A(2);
        if (n) gwrite(d, gread(s, n)); RET(d);
    });
    if (name == "memset") return regHook([this]{
        uint64_t d = A(0), c = A(1), n = A(2);
        std::vector<uint8_t> b(n, (uint8_t)c); if (n) gwrite(d, b); RET(d);
    });
    if (name == "memcmp") return regHook([this]{
        uint64_t n = A(2); auto a = gread(A(0), n), b = gread(A(1), n);
        for (uint64_t i = 0; i < n; i++) if (a[i] != b[i]) { RET(a[i] < b[i] ? (uint64_t)-1 : 1); return; }
        RET(0);
    });
    if (name == "memchr") return regHook([this]{
        uint64_t a = A(0); uint8_t c = (uint8_t)A(1); uint64_t n = A(2);
        auto m = gread(a, n);
        for (uint64_t i = 0; i < n; i++) if (m[i] == c) { RET(a + i); return; }
        RET(0);
    });
    if (name == "strlen") return regHook([this]{ RET(greadCStr(A(0)).size()); });
    if (name == "strcmp") return regHook([this]{
        auto a = greadStr(A(0)), b = greadStr(A(1));
        RET(a < b ? (uint64_t)-1 : (a > b ? 1 : 0));
    });
    if (name == "strncmp") return regHook([this]{
        uint64_t n = A(2);
        auto a = greadStr(A(0)).substr(0, n), b = greadStr(A(1)).substr(0, n);
        RET(a < b ? (uint64_t)-1 : (a > b ? 1 : 0));
    });
    if (name == "strcpy") return regHook([this]{
        uint64_t d = A(0); auto s = greadCStr(A(1));
        gwrite(d, s); uint8_t z = 0; gwrite(d + s.size(), &z, 1); RET(d);
    });
    if (name == "strncpy") return regHook([this]{
        uint64_t d = A(0), n = A(2); auto s = greadCStr(A(1));
        std::vector<uint8_t> buf(n, 0);
        for (uint64_t i = 0; i < n && i < s.size(); i++) buf[i] = s[i];
        gwrite(d, buf); RET(d);
    });
    if (name == "strchr") return regHook([this]{
        uint64_t a = A(0); uint8_t c = (uint8_t)A(1); auto s = greadCStr(a);
        for (size_t i = 0; i < s.size(); i++) if (s[i] == c) { RET(a + i); return; }
        RET(0);
    });
    if (name == "strrchr") return regHook([this]{
        uint64_t a = A(0); uint8_t c = (uint8_t)A(1); auto s = greadCStr(a);
        for (int i = (int)s.size() - 1; i >= 0; i--) if (s[i] == c) { RET(a + i); return; }
        RET(0);
    });
    if (name == "strstr") return regHook([this]{
        uint64_t a = A(0); auto h = greadStr(a), nd = greadStr(A(1));
        auto pos = h.find(nd);
        RET(pos == std::string::npos ? 0 : a + pos);
    });
    if (name == "strdup") return regHook([this]{
        auto s = greadCStr(A(0)); uint64_t p = gmalloc(s.size() + 1);
        gwrite(p, s); uint8_t z = 0; gwrite(p + s.size(), &z, 1); RET(p);
    });
    if (name == "strtol") return regHook([this]{
        auto s = greadStr(A(0)); int base = A(2) ? (int)A(2) : 10;
        RET((int64_t)strtol(s.c_str(), nullptr, base));
    });
    if (name == "strtoul") return regHook([this]{
        auto s = greadStr(A(0)); int base = A(2) ? (int)A(2) : 10;
        RET((uint64_t)strtoul(s.c_str(), nullptr, base));
    });
    if (name == "atoi") return regHook([this]{ RET((int64_t)atoi(greadStr(A(0)).c_str())); });
    if (name == "strcspn") return regHook([this]{
        auto s = greadStr(A(0)), set = greadStr(A(1));
        size_t i = 0; for (; i < s.size(); i++) if (set.find(s[i]) != std::string::npos) break;
        RET(i);
    });
    if (name == "strspn") return regHook([this]{
        auto s = greadStr(A(0)), set = greadStr(A(1));
        size_t i = 0; for (; i < s.size(); i++) if (set.find(s[i]) == std::string::npos) break;
        RET(i);
    });
    if (name == "strpbrk") return regHook([this]{
        uint64_t a = A(0); auto s = greadStr(a), set = greadStr(A(1));
        for (size_t i = 0; i < s.size(); i++) if (set.find(s[i]) != std::string::npos) { RET(a + i); return; }
        RET(0);
    });
    if (name == "__errno") return regHook([this]{ RET(errnoLoc); });
    if (name == "time") return regHook([this]{
        uint64_t t = 1700000000; if (A(0)) gwrite64(A(0), t); RET(t);
    });
    if (name == "clock_gettime") return regHook([this]{
        uint64_t ts = A(1); if (ts) { gwrite64(ts, 1700000000); gwrite64(ts + 8, 0); } RET(0);
    });
    if (name == "gettimeofday") return regHook([this]{
        uint64_t tv = A(0); if (tv) { gwrite64(tv, 1700000000); gwrite64(tv + 8, 0); } RET(0);
    });
    if (name == "rand") return regHook([this]{ RET((rnd() >> 16) & 0x7fff); });
    if (name == "srand") return regHook([this]{ rng = (uint32_t)A(0); RET(0); });
    if (name == "getentropy") return regHook([this]{
        uint64_t a = A(0), n = A(1); std::vector<uint8_t> b(n);
        for (uint64_t i = 0; i < n; i++) b[i] = (uint8_t)((rnd() >> 16) & 0xff);
        gwrite(a, b); RET(0);
    });
    if (name == "getpid") return regHook([this]{ RET(1234); });
    if (name == "sysconf") return regHook([this]{ RET(4096); });
    if (name == "mmap") return regHook([this]{ RET(gmmap(A(1))); });
    if (name == "abort") return regHook([this]{ LOGI("[guest abort]"); uc_emu_stop(uc); });
    if (name == "__stack_chk_fail") return regHook([this]{ LOGI("[stack_chk_fail]"); uc_emu_stop(uc); });
    if (name == "__system_property_get") return regHook([this]{
        if (A(1)) { uint8_t z = 0; gwrite(A(1), &z, 1); } RET(0);
    });
    if (name == "getenv") return regHook([this]{ RET(0); });
    if (name == "getauxval") return regHook([this]{
        uint64_t t = A(0); RET(t == 16 ? 0x2 : (t == 6 ? 4096 : 0));
    });
    if (name == "pthread_self") return regHook([this]{ RET(1); });
    if (name == "syscall") return regHook([this]{
        uint64_t n = A(0);
        if (n == 278) {
            uint64_t buf = A(1), len = A(2); std::vector<uint8_t> b(len);
            for (uint64_t i = 0; i < len; i++) b[i] = (uint8_t)((rnd() >> 16) & 0xff);
            gwrite(buf, b); RET(len); return;
        }
        RET(0);
    });
    if (name == "snprintf") return regHook([this]{
        if (A(1)) { uint8_t z = 0; gwrite(A(0), &z, 1); } RET(0);
    });
    if (name == "pthread_key_create") return regHook([this]{
        static int nextKey = 1;
        int k = nextKey++;
        if (A(0)) { uint8_t b[4] = {(uint8_t)k,0,0,0}; gwrite(A(0), b, 4); }
        RET(0);
    });
    if (name == "pthread_key_delete") return regHook([this]{ RET(0); });
    if (name == "pthread_setspecific") return regHook([this]{
        static std::map<uint64_t,uint64_t> tlsVals;
        tlsVals[A(0)] = A(1); RET(0);
    });
    if (name == "pthread_getspecific") return regHook([this]{ RET(0); });
    if (name == "pthread_once") return regHook([this]{
        uint64_t ctrl = A(0), init = A(1); RET(0);
        if (gread32(ctrl) == 0) { uint8_t b[4] = {1,0,0,0}; gwrite(ctrl, b, 4); redirect = (int64_t)init; }
    });

    return regHook(stub0);
}

void Emulator::setupJniHandlers(uint64_t table, uint64_t /*unused*/) {
    auto set32at = [&](uint64_t off, uint64_t addr) {
        uint8_t b[8];
        for (int i = 0; i < 8; i++) { b[i] = addr & 0xff; addr >>= 8; }
        gwrite(table + off, b, 8);
    };

    set32at(0x30, regHook([this]{ RET(H_CLASS); })); // FindClass
    set32at(0x88, regHook([this]{ RET(0); })); // ExceptionClear
    set32at(0xb8, regHook([this]{ RET(0); })); // DeleteLocalRef
    set32at(0xf8, regHook([this]{ RET(H_CLASS); })); // GetObjectClass
    set32at(0x108, regHook([this]{ RET(H_MID); })); // GetMethodID
    set32at(0x118, regHook([this]{ RET(H_INARR); })); // CallObjectMethodV
    set32at(0x388, regHook([this]{ RET(H_MID); })); // GetStaticMethodID
    set32at(0x398, regHook([this]{
        std::string key = getHelp(lastNewStr);
        LOGI("[getHelp] marker len=%zu keylen=%zu", lastNewStr.size(), key.size());
        RET(mkJString(key));
    })); // CallStaticObjectMethodV
    set32at(0x538, regHook([this]{
        lastNewStr = greadStr(A(1));
        RET(mkJString(lastNewStr));
    })); // NewStringUTF
    set32at(0x540, regHook([this]{ RET(gread32(A(1))); })); // GetStringUTFLength
    set32at(0x548, regHook([this]{
        if (A(2)) { uint8_t b[4] = {0,0,0,0}; gwrite(A(1) ? A(2) : A(2), b, 4); }
        RET(A(1) + 4);
    })); // GetStringUTFChars
    set32at(0x550, regHook([this]{ RET(0); })); // ReleaseStringUTFChars
    set32at(0x558, regHook([this]{ RET(A(1) == H_INARR ? inBytes.size() : outLen); })); // GetArrayLength
    set32at(0x580, regHook([this]{ outLen = A(1); RET(H_OUTARR); })); // NewByteArray
    set32at(0x5c0, regHook([this]{
        if (A(2)) { uint8_t b[4] = {0,0,0,0}; gwrite(A(2), b, 4); }
        RET(inArrGuest);
    })); // GetByteArrayElements
    set32at(0x600, regHook([this]{ RET(0); })); // ReleaseByteArrayElements
    set32at(0x680, regHook([this]{
        uint64_t start = A(2), len = A(3), buf = A(4);
        if (start + len <= out.size()) {
            auto data = gread(buf, len);
            std::copy(data.begin(), data.end(), out.begin() + start);
            haveOut = true;
        }
        RET(0);
    })); // SetByteArrayRegion
    set32at(0x720, regHook([this]{ RET(0); })); // ExceptionCheck
    set32at(0x78, regHook([this]{ RET(0); })); // ExceptionOccurred
    set32at(0x35c, regHook([this]{
        LOGI("[ThrowNew] %s", A(2) ? greadStr(A(2)).c_str() : "");
        RET(0);
    })); // ThrowNew
}

static uint32_t ru32(const std::vector<uint8_t>& b, size_t o) {
    return b[o] | (b[o+1]<<8) | (b[o+2]<<16) | (b[o+3]<<24);
}
static uint64_t ru64(const std::vector<uint8_t>& b, size_t o) {
    return ru32(b, o) | ((uint64_t)ru32(b, o+4) << 32);
}
static uint16_t ru16(const std::vector<uint8_t>& b, size_t o) {
    return b[o] | (b[o+1] << 8);
}

bool Emulator::loadElfAndRun(const std::string& entrySymbol) {
    if (uc_open(UC_ARCH_ARM64, UC_MODE_LITTLE_ENDIAN, &uc) != UC_ERR_OK) {
        LOGE("uc_open failed");
        return false;
    }

    uint64_t e_phoff = ru64(soBytes, 0x20);
    uint16_t e_phnum = ru16(soBytes, 0x38);

    uint64_t maxv = 0;
    struct Load { uint64_t off, va, fsz; };
    std::vector<Load> loads;
    uint64_t dynVa = 0;

    for (int i = 0; i < e_phnum; i++) {
        uint64_t p = e_phoff + i * 56;
        uint32_t type = ru32(soBytes, p);
        if (type == 1) { // PT_LOAD
            uint64_t off = ru64(soBytes, p + 8), va = ru64(soBytes, p + 16);
            uint64_t fsz = ru64(soBytes, p + 32), msz = ru64(soBytes, p + 40);
            loads.push_back({off, va, fsz});
            if (va + msz > maxv) maxv = va + msz;
        } else if (type == 2) { // PT_DYNAMIC
            dynVa = ru64(soBytes, p + 16);
        }
    }

    uint64_t span = ((maxv + 0xffff) / 0x10000) * 0x10000;
    std::vector<uint8_t> sobk(span, 0);
    for (auto& L : loads) {
        for (uint64_t i = 0; i < L.fsz && (L.off + i) < soBytes.size(); i++)
            sobk[L.va + i] = soBytes[L.off + i];
    }

    uint64_t rela=0, relasz=0, jmprel=0, pltsz=0, symtab=0, strtab=0, syment=24, initarr=0, initarrsz=0;
    for (uint64_t d = dynVa; ; d += 16) {
        uint64_t tag = ru64(sobk, d), val = ru64(sobk, d + 8);
        if (tag == 0) break;
        if (tag == 7) rela = val;
        else if (tag == 8) relasz = val;
        else if (tag == 23) jmprel = val;
        else if (tag == 2) pltsz = val;
        else if (tag == 6) symtab = val;
        else if (tag == 5) strtab = val;
        else if (tag == 11) syment = val;
        else if (tag == 25) initarr = val;
        else if (tag == 27) initarrsz = val;
    }

    auto symName = [&](uint64_t idx) -> std::string {
        uint32_t nameOff = ru32(sobk, symtab + idx * syment);
        std::string s;
        for (uint64_t i = strtab + nameOff; sobk[i]; i++) s += (char)sobk[i];
        return s;
    };
    auto symShndx = [&](uint64_t idx) { return ru16(sobk, symtab + idx * syment + 6); };
    auto symValue = [&](uint64_t idx) { return ru64(sobk, symtab + idx * syment + 8); };

    auto wset64 = [&](uint64_t off, uint64_t v) {
        for (int i = 0; i < 8; i++) { sobk[off + i] = v & 0xff; v >>= 8; }
    };

    std::map<uint32_t,int> unhandledTypes;
    int zeroWrites = 0;
    auto applyRelocs = [&](uint64_t r, uint64_t sz) {
        for (uint64_t o = 0; o < sz; o += 24) {
            uint64_t off = ru64(sobk, r + o);
            uint32_t type = ru32(sobk, r + o + 8);
            uint32_t symi = ru32(sobk, r + o + 12);
            uint64_t add = ru64(sobk, r + o + 16);

            if (type == 1027) { // RELATIVE
                wset64(off, BASE + add);
            } else if (type == 1026 || type == 1025 || type == 257) { // JUMP_SLOT/GLOB_DAT/ABS64
                if (symShndx(symi)) {
                    wset64(off, BASE + symValue(symi) + (type == 257 ? add : 0));
                } else {
                    uint64_t resolved = resolveImport(symName(symi));
                    if (resolved == 0) zeroWrites++;
                    wset64(off, resolved);
                }
            } else if (type != 0) {
                unhandledTypes[type]++;
            }
        }
    };
    applyRelocs(rela, relasz);
    applyRelocs(jmprel, pltsz);

    diagLog += "unhandled reloc types: ";
    for (auto& kv : unhandledTypes) diagLog += std::to_string(kv.first) + "x" + std::to_string(kv.second) + " ";
    diagLog += "\nzero-address writes: " + std::to_string(zeroWrites) + "\n";

    errnoLoc = gmalloc(8);
    inArrGuest = gmalloc(inBytes.size());

    uint64_t table = gmalloc(0x800);
    std::vector<uint8_t> tbuf(0x800, 0);
    // временно пишем нулевую таблицу, потом дозаполняем через setupJniHandlers
    uc_mem_map(uc, BASE, span, UC_PROT_ALL);
    uc_mem_map(uc, STACK_BASE, STACK_SIZE, UC_PROT_ALL);
    uc_mem_map(uc, HEAP_BASE, HEAP_SIZE, UC_PROT_ALL);
    uc_mem_map(uc, MMAP_BASE, MMAP_SIZE, UC_PROT_ALL);
    uc_mem_map(uc, TLS_BASE, TLS_SIZE, UC_PROT_ALL);

    std::vector<uint8_t> hookpage(0x1000);
    for (int i = 0; i < 0x1000; i += 4) {
        hookpage[i]=0xc0; hookpage[i+1]=0x03; hookpage[i+2]=0x5f; hookpage[i+3]=0xd6;
    }
    uc_mem_map(uc, HOOKBASE, 0x1000, UC_PROT_ALL);
    gwrite(HOOKBASE, hookpage);

    gwrite(BASE, sobk);

    setupJniHandlers(table, 0);
    gwrite(table, tbuf.data(), tbuf.size()); // перезапишется правильно ниже через set32at внутри setupJniHandlers

    uint64_t envp = gmalloc(8);
    gwrite64(envp, table);
    gwrite64(errnoLoc, 0);
    gwrite(inArrGuest, inBytes);

    regSet(UC_ARM64_REG_TPIDR_EL0, TLS_BASE + 0x1000);
    gwrite64(TLS_BASE + 0x28, 0);
    regSet(UC_ARM64_REG_SP, STACK_BASE + STACK_SIZE - 0x100);

    uc_hook trace;
    uc_hook_add(uc, &trace, UC_HOOK_CODE, (void*)hookCodeTrampoline, this, HOOKBASE, HOOKBASE + 0x800);

    uc_hook fetchHook;
    uc_hook_add(uc, &fetchHook, UC_HOOK_MEM_FETCH_UNMAPPED,
        (void*)+[](uc_engine* u, uc_mem_type type, uint64_t address, int size, int64_t value, void* ud) -> bool {
            auto* e = (Emulator*)ud;
            e->lastFetchAddr = address;
            uc_reg_read(u, UC_ARM64_REG_PC, &e->lastPcBeforeFetch);
            return false;
        }, this, 1, 0);

    // Трассировка последних выполненных адресов (кольцевой буфер) для диагностики без logcat.
    uc_hook traceHook;
    uc_hook_add(uc, &traceHook, UC_HOOK_CODE,
        (void*)+[](uc_engine* u, uint64_t address, uint32_t size, void* ud) {
            auto* e = (Emulator*)ud;
            e->traceRing[e->traceIdx % 16] = address;
            e->traceIdx++;
        }, this, BASE, BASE + span);

    if (initarr) {
        for (uint64_t o = 0; o < initarrsz; o += 8) {
            uint64_t fn = ru64(sobk, initarr + o);
            if (!fn) continue;
            regSet(UC_ARM64_REG_X30, HOOK_STOP);
            regSet(UC_ARM64_REG_SP, STACK_BASE + STACK_SIZE - 0x100);
            uc_err e = uc_emu_start(uc, BASE + fn, HOOK_STOP, 0, 0);
            if (e != UC_ERR_OK) LOGI("[init err] %s", uc_strerror(e));
        }
    }

    uint64_t entry = 0;
    for (uint64_t so = symtab; so < strtab; so += syment) {
        uint16_t shndx = ru16(sobk, so + 6);
        uint32_t nameOff = ru32(sobk, so);
        if (shndx && nameOff) {
            std::string s;
            for (uint64_t i = strtab + nameOff; sobk[i]; i++) s += (char)sobk[i];
            if (s == entrySymbol) { entry = BASE + ru64(sobk, so + 8); break; }
        }
    }
    if (!entry) { LOGE("entry symbol not found"); return false; }
    LOGI("[entry] file 0x%llx inLen=%zu", (unsigned long long)(entry - BASE), inBytes.size());
    diagLog += "entry found at 0x" + std::to_string(entry - BASE) + "\n";

    regSet(RX(0), envp);
    regSet(RX(1), 1);
    regSet(RX(2), H_INARR);
    regSet(UC_ARM64_REG_SP, STACK_BASE + STACK_SIZE - 0x100);
    regSet(UC_ARM64_REG_X30, HOOK_STOP);

    uc_err e = uc_emu_start(uc, entry, HOOK_STOP, 0, 0);
    diagLog += "emu_start result: " + std::string(uc_strerror(e)) + " (code " + std::to_string((int)e) + ")\n";
    if (e != UC_ERR_OK) LOGE("[emu_start error] %s", uc_strerror(e));

    uint64_t pcAfter = regGet(UC_ARM64_REG_PC);
    diagLog += "PC after stop: 0x" + std::to_string(pcAfter) + "\n";
    diagLog += "fetch attempted at: 0x" + std::to_string(lastFetchAddr) + "\n";
    diagLog += "PC before bad fetch: 0x" + std::to_string(lastPcBeforeFetch) + "\n";
    diagLog += "trace (last executed, oldest->newest, file offsets): ";
    int total = traceIdx < 16 ? traceIdx : 16;
    for (int i = 0; i < total; i++) {
        uint64_t addr = traceRing[(traceIdx - total + i) % 16];
        uint64_t off = addr >= BASE ? addr - BASE : addr;
        diagLog += "0x" + std::to_string(off) + " ";
    }
    diagLog += "\n";
    diagLog += "handlers registered: " + std::to_string(handlers.size()) + "\n";

    LOGI("[done] haveOut=%d outLen=%llu", haveOut, (unsigned long long)outLen);
    uc_close(uc);
    return haveOut;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_cheremsha_decrypt_crypt_app_crypto_UnicornBridge_checkUnicornVersion(
        JNIEnv *env, jobject) {
    unsigned int major, minor;
    uc_version(&major, &minor);
    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u", major, minor);
    return env->NewStringUTF(buf);
}

static std::string g_lastDiag;

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_github_cheremsha_decrypt_crypt_app_crypto_UnicornBridge_decryptCrypt5(
        JNIEnv *env, jobject, jbyteArray soBytesArr, jbyteArray inputArr) {

    Emulator emu;
    emu.realEnv = env;

    jsize soLen = env->GetArrayLength(soBytesArr);
    jbyte* soPtr = env->GetByteArrayElements(soBytesArr, nullptr);
    emu.soBytes.assign((uint8_t*)soPtr, (uint8_t*)soPtr + soLen);
    env->ReleaseByteArrayElements(soBytesArr, soPtr, JNI_ABORT);

    jsize inLen = env->GetArrayLength(inputArr);
    jbyte* inPtr = env->GetByteArrayElements(inputArr, nullptr);
    emu.inBytes.assign((uint8_t*)inPtr, (uint8_t*)inPtr + inLen);
    env->ReleaseByteArrayElements(inputArr, inPtr, JNI_ABORT);

    bool ok = emu.loadElfAndRun("Java_su_happ_proxyutility_util_ErrorCodeJNIWrapper_jniGetErrorMessageFromString2");
    g_lastDiag = emu.diagLog + "loadElfAndRun returned: " + (ok ? "true" : "false");

    if (!ok || emu.outLen == 0) {
        return env->NewByteArray(0);
    }

    jbyteArray result = env->NewByteArray((jsize)emu.outLen);
    env->SetByteArrayRegion(result, 0, (jsize)emu.outLen, (const jbyte*)emu.out.data());
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_cheremsha_decrypt_crypt_app_crypto_UnicornBridge_getLastDiag(
        JNIEnv *env, jobject) {
    return env->NewStringUTF(g_lastDiag.c_str());
}
