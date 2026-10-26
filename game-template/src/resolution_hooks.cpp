// FIFA's device setup fixes the back buffer at 720p even when XGetVideoMode is smaller.
#include <rex/hook.h>
#include <rex/ui/flags.h>

REX_EXTERN(__imp__sub_826FD4B8);
REX_EXTERN(__imp__sub_826CDA38);

REX_HOOK_RAW(sub_826FD4B8) {
#if defined(__ANDROID__)
  const int32_t width = REXCVAR_GET(video_mode_width);
  const int32_t height = REXCVAR_GET(video_mode_height);
  // Only the verified FIFA device-creation call has this presentation-parameter layout.
  if (ctx.lr == 0x82A515B0 && ctx.r4.u32 == 1 && ctx.r7.u32 &&
      ((width == 960 && height == 540) || (width == 854 && height == 480))) {
    auto* parameters = reinterpret_cast<rex::be<uint32_t>*>(base + ctx.r7.u32);
    if ((parameters[0] == 1280 || parameters[0] == 960) && parameters[1] == 720) {
      parameters[0] = uint32_t(width);
      parameters[1] = uint32_t(height);
      REXKRNL_INFO("FIFA back buffer requested: {}x{}", width, height);
    }
  }
#endif
  __imp__sub_826FD4B8(ctx, base);
}

REX_HOOK_RAW(sub_826CDA38) {
  const uint32_t layout = ctx.r3.u32;
  __imp__sub_826CDA38(ctx, base);
#if defined(__ANDROID__)
  const int32_t width = REXCVAR_GET(video_mode_width);
  const int32_t height = REXCVAR_GET(video_mode_height);
  if (!layout || layout > 0xFFFFFFFFu - (528 + 16 * 4) ||
      !((width == 960 && height == 540) || (width == 854 && height == 480))) return;

  // This routine rebuilds the active view rectangles with hard-coded 1280/720 dimensions.
  auto* views = reinterpret_cast<rex::be<uint32_t>*>(base + layout + 528);
  bool changed = false;
  uint32_t active = 0, scaled = 0, invalid = 0;
  for (uint32_t i = 0; i < 16; ++i) {
    const uint32_t address = views[i];
    if (!address || address > 0xFFFFFFFFu - 52 || !base[address + 28]) continue;
    ++active;
    auto* view = reinterpret_cast<rex::be<uint32_t>*>(base + address);
    const uint32_t x = view[5], y = view[6], w = view[11], h = view[12];
    if (!w || !h || uint64_t(x) + w > 1280 || uint64_t(y) + h > 720) {
      ++invalid;
      continue;
    }
    const uint32_t left = uint32_t(uint64_t(x) * width / 1280);
    const uint32_t top = uint32_t(uint64_t(y) * height / 720);
    const uint32_t right = uint32_t((uint64_t(x) + w) * width / 1280);
    const uint32_t bottom = uint32_t((uint64_t(y) + h) * height / 720);
    if (right <= left || bottom <= top) continue;
    view[5] = left;
    view[6] = top;
    view[11] = right - left;
    view[12] = bottom - top;
    changed = true;
    ++scaled;
  }
  static uint32_t calls = 0;
  ++calls;
  if (calls && !(calls & (calls - 1))) {
    REXKRNL_INFO("FIFA view hook: call={} layout={:08X} active={} scaled={} invalid={} target={}x{}",
                 calls, layout, active, scaled, invalid, width, height);
  }
  static bool logged = false;
  if (changed && !logged) {
    REXKRNL_INFO("FIFA view layout scaled from 1280x720 to {}x{}", width, height);
    logged = true;
  }
#endif
}
