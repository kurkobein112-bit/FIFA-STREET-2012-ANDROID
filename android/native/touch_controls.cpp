#include <jni.h>
#include <SDL3/SDL.h>
#include <algorithm>
#include <mutex>
#include <cmath>

namespace {
std::mutex mutex;
SDL_Joystick* joystick = nullptr;
SDL_JoystickID id = 0;
void Disconnect() {
  if (joystick) SDL_CloseJoystick(joystick);
  joystick = nullptr;
  if (id) SDL_DetachVirtualJoystick(id);
  id = 0;
}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_restreet_android_preview_TouchControls_connect(JNIEnv*, jclass) {
  std::lock_guard lock(mutex);
  if (joystick) return true;
  if (!(SDL_WasInit(SDL_INIT_GAMEPAD) & SDL_INIT_GAMEPAD)) return false;
  SDL_VirtualJoystickDesc desc;
  SDL_INIT_INTERFACE(&desc);
  desc.type = SDL_JOYSTICK_TYPE_GAMEPAD;
  desc.naxes = 6; desc.nbuttons = 15;
  desc.axis_mask = (1u << 6) - 1;
  desc.button_mask = (1u << 15) - 1;
  desc.name = "FIFA Street Touch Controller";
  id = SDL_AttachVirtualJoystick(&desc);
  if (!id) return false;
  joystick = SDL_OpenJoystick(id);
  if (!joystick) { Disconnect(); return false; }
  SDL_SetJoystickVirtualAxis(joystick, 4, -32768);
  SDL_SetJoystickVirtualAxis(joystick, 5, -32768);
  return true;
}
extern "C" JNIEXPORT void JNICALL
Java_org_restreet_android_preview_TouchControls_disconnect(JNIEnv*, jclass) {
  std::lock_guard lock(mutex); Disconnect();
}
extern "C" JNIEXPORT void JNICALL
Java_org_restreet_android_preview_TouchControls_axis(JNIEnv*, jclass, jint index, jfloat value) {
  std::lock_guard lock(mutex);
  if (!joystick || index < 0 || index >= 6 || !std::isfinite(value)) return;
  SDL_SetJoystickVirtualAxis(joystick, index, static_cast<Sint16>(std::clamp(value, -1.0f, 1.0f) * 32767));
}
extern "C" JNIEXPORT void JNICALL
Java_org_restreet_android_preview_TouchControls_button(JNIEnv*, jclass, jint index, jboolean down) {
  std::lock_guard lock(mutex);
  if (joystick && index >= 0 && index < 15) SDL_SetJoystickVirtualButton(joystick, index, down);
}
