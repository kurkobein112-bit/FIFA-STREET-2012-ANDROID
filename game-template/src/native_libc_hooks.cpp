// Native replacements for the three C runtime routines the game calls most (memcpy, memset, memmove).
//
// The recompiled versions run as byte/word loops of volatile, byte-swapped loads and stores, which
// the compiler cannot vectorise. Their guest register contract is simple (r3 = destination, r4 =
// source or fill byte, r5 = byte count), so a plain host memcpy/memset/memmove does the same work.
// Anything unusual falls back to the original recompiled body (__imp__sub_XXXXXXXX):
//   - ranges that touch the MMIO window: the MMIO handler only decodes plain `mov` instructions;
//   - ranges that straddle the 0xE0000000 physical alias (host offset changes there);
//   - overlapping memcpy ranges (the original copies forward, a host memcpy is undefined).
// Set REX_NATIVE_LIBC_HOOKS=0 to use the original guest code (A/B testing).
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include <rex/hook.h>

REX_EXTERN(__imp__sub_82738530);  // memcpy
REX_EXTERN(__imp__sub_82737D50);  // memset
REX_EXTERN(__imp__sub_82738F80);  // memmove

namespace {

bool HooksEnabled() {
  static const bool enabled = [] {
    const char* value = std::getenv("REX_NATIVE_LIBC_HOOKS");
    return !(value && value[0] == '0');
  }();
  return enabled;
}

inline uint8_t* HostAddress(uint8_t* base, uint32_t address) {
#if REX_PLATFORM_WIN32 || (REX_PLATFORM_MAC && REX_ARCH_ARM64)
  return base + address + (address >= 0xE0000000u ? 0x1000u : 0u);
#else
  // Match REX_PHYS_HOST_OFFSET: Linux/Android map physical aliases without a gap.
  return base + address;
#endif
}

// True when [address, address + size) is ordinary guest RAM that host code may touch directly.
inline bool PlainRange(uint32_t address, uint32_t size) {
  const uint64_t end = uint64_t(address) + size;
  if (end > 0x100000000ull) return false;
  if (address < 0x80000000u && end > 0x7F000000u) return false;  // MMIO 0x7F000000-0x7FFFFFFF.
  if (address < 0xE0000000u && end > 0xE0000000u) return false;
  return true;
}

inline bool Overlaps(uint32_t a, uint32_t b, uint32_t size) {
  return a < uint64_t(b) + size && b < uint64_t(a) + size;
}

}  // namespace

REX_HOOK_RAW(sub_82738530) {
  const uint32_t destination = ctx.r3.u32, source = ctx.r4.u32, size = ctx.r5.u32;
  if (HooksEnabled() && PlainRange(destination, size) && PlainRange(source, size) &&
      !Overlaps(destination, source, size)) {
    if (size) std::memcpy(HostAddress(base, destination), HostAddress(base, source), size);
    return;  // r3 still holds the destination, as the original returns it.
  }
  __imp__sub_82738530(ctx, base);
}

REX_HOOK_RAW(sub_82737D50) {
  const uint32_t destination = ctx.r3.u32, size = ctx.r5.u32;
  if (HooksEnabled() && PlainRange(destination, size)) {
    if (size) std::memset(HostAddress(base, destination), int(ctx.r4.u32 & 0xFF), size);
    return;
  }
  __imp__sub_82737D50(ctx, base);
}

REX_HOOK_RAW(sub_82738F80) {
  const uint32_t destination = ctx.r3.u32, source = ctx.r4.u32, size = ctx.r5.u32;
  if (HooksEnabled() && PlainRange(destination, size) && PlainRange(source, size)) {
    if (size && destination != source) {
      std::memmove(HostAddress(base, destination), HostAddress(base, source), size);
    }
    return;
  }
  __imp__sub_82738F80(ctx, base);
}

// ---------------------------------------------------------------------------------------------
// Game frame limiter (sub_82613E70): Timer::LimitFramerate(timer = r3, rate = r4). It busy-waits
// (a loop of `or r31,r31,r31` priority hints and timebase reads) until timer.start + frequency / rate.
// The game calls it with three fixed rates: 200 (main loop throttle), 60 (gameplay) and 40 (replays and
// cutscenes), which is the 40 FPS seen there on every GPU. REX_FRAME_LIMITER_MIN_HZ raises any rate
// below it to that value (0 or unset leaves the game's own rates).
// ---------------------------------------------------------------------------------------------
REX_EXTERN(__imp__sub_82613E70);

REX_HOOK_RAW(sub_82613E70) {
  static const uint32_t min_rate = [] {
    const char* value = std::getenv("REX_FRAME_LIMITER_MIN_HZ");
    // "auto": follow the refresh rate chosen in the launcher (REX_VIDEO_MODE_REFRESH_RATE).
    if (value && std::strcmp(value, "auto") == 0) value = std::getenv("REX_VIDEO_MODE_REFRESH_RATE");
    const double parsed = value ? std::strtod(value, nullptr) : 0.0;
    return uint32_t(parsed >= 30.0 && parsed <= 10000.0 ? parsed + 0.5 : 0.0);
  }();
  static bool logged_original[256] = {};
  const uint32_t rate = ctx.r4.u32;
  if (rate < 256 && !logged_original[rate]) {
    logged_original[rate] = true;
    REXKRNL_INFO("Frame limiter call: game rate {} Hz, minimum {} Hz", rate, min_rate);
  }
  if (min_rate && rate && rate < min_rate) {
    ctx.r4.u64 = min_rate;
  }
  __imp__sub_82613E70(ctx, base);
}
