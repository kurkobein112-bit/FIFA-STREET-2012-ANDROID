// fifastreet - ReXGlue Recompiled Project
//
// Customize your app by overriding virtual hooks from rex::ReXApp.

#pragma once

#include <rex/rex_app.h>
#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/logging.h>
#include <rex/ui/window.h>


class FifastreetApp : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<FifastreetApp>(new FifastreetApp(ctx, "fifastreet",
        PPCImageConfig));
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    if (config.gpu_plugin.empty()) {
      config.gpu_plugin = "xenos";
    }
  }

  void OnConfigurePaths(rex::PathConfig& paths) override {
#if defined(__ANDROID__)
    paths.config_path = paths.user_data_root / "fifastreet.toml";
#endif
    if (paths.game_data_root.empty()) {
      const auto executable_folder = rex::filesystem::GetExecutableFolder();
      for (const auto& candidate : {executable_folder, executable_folder / "GameData",
                                   executable_folder.parent_path() / "GameData",
                                   executable_folder.parent_path()}) {
        std::error_code error;
        if (std::filesystem::is_regular_file(candidate / "default.xex", error)) {
          paths.game_data_root = candidate;
          break;
        }
      }
    }
  }

  void OnCreateDialogs(rex::ui::ImGuiDrawer*) override {
#if defined(__ANDROID__)
    window()->SetTitle("FIFA Street Android");
#else
    window()->SetTitle("FIFA Street PC");
#endif
  }

  void OnPostSetup() override {
    // FIFA Street uses texture fetch descriptors with the invalid-texture tag.
    // Their address and format fields are still valid and must be decoded.
    rex::cvar::SetFlagByName("gpu_allow_invalid_fetch_constants", "true");
#if defined(__ANDROID__)
    // Repeated guest debug traps flush the log on every hit; retain CPU errors.
    if (REXCVAR_GET(log_level) == "info" && !REXCVAR_GET(log_verbose)) {
      rex::SetCategoryLevel(rex::log::cpu(), spdlog::level::err);
    }
#endif
  }


  // Override virtual hooks for customization:
  // void OnPostInitLogging() override {}
  // void OnPreSetup(rex::RuntimeConfig& config) override {}
  // void OnLoadXexImage(std::string& xex_image) override {}
  // void OnPostLoadXexImage() override {}
  // void OnPostSetup() override {}
  // void OnCreateDialogs(rex::ui::ImGuiDrawer* drawer) override {}
  // std::unique_ptr<rex::ui::ImGuiDialog> CreateAchievementsOverlay() override;
  // std::unique_ptr<rex::ui::AchievementNotificationDialog>
  // CreateAchievementNotificationDialog() override;
  // void OnShutdown() override {}
  // void OnConfigurePaths(rex::PathConfig& paths) override {}
};
