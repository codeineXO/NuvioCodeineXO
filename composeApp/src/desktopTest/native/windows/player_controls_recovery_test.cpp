// Run with run-player-controls-recovery-tests.ps1. Uses a hidden WebView and an isolated profile.
#include <windows.h>
#include <windowsx.h>
#include <d3d11.h>
#include <dwmapi.h>
#include <shlobj.h>
#include <wrl.h>
#include <WebView2.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cwctype>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

using Microsoft::WRL::Callback;
using Microsoft::WRL::ComPtr;
static std::wstring testProfile;
static std::wstring testDataRoot;
static unsigned environmentCalls = 0;
static unsigned failEnvironmentCalls = 0;
static unsigned failEnvironmentCallbacks = 0;
static unsigned failControllerCalls = 0;
static unsigned failControllerCallbacks = 0;
static bool stallController = false;
static ComPtr<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler> stalledCallback;

static HRESULT TestGetFolderPath(HWND, int, HANDLE, DWORD, LPWSTR path) {
    return wcscpy_s(path, MAX_PATH, testDataRoot.c_str()) == 0 ? S_OK : E_FAIL;
}

class TestEnvironment : public Microsoft::WRL::RuntimeClass<
    Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::ClassicCom>, ICoreWebView2Environment> {
public:
    ComPtr<ICoreWebView2Environment> inner;
    HRESULT STDMETHODCALLTYPE CreateCoreWebView2Controller(HWND window,
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *handler) override {
        if (failControllerCalls) { --failControllerCalls; return E_FAIL; }
        if (failControllerCallbacks) { --failControllerCallbacks; handler->Invoke(E_FAIL, nullptr); return S_OK; }
        if (stallController) { stallController = false; stalledCallback = handler; return S_OK; }
        return inner->CreateCoreWebView2Controller(window, handler);
    }
    HRESULT STDMETHODCALLTYPE CreateWebResourceResponse(IStream *content, int status,
        LPCWSTR reason, LPCWSTR headers, ICoreWebView2WebResourceResponse **response) override {
        return inner->CreateWebResourceResponse(content, status, reason, headers, response);
    }
    HRESULT STDMETHODCALLTYPE get_BrowserVersionString(LPWSTR *version) override {
        return inner->get_BrowserVersionString(version);
    }
    HRESULT STDMETHODCALLTYPE add_NewBrowserVersionAvailable(
        ICoreWebView2NewBrowserVersionAvailableEventHandler *handler, EventRegistrationToken *token) override {
        return inner->add_NewBrowserVersionAvailable(handler, token);
    }
    HRESULT STDMETHODCALLTYPE remove_NewBrowserVersionAvailable(EventRegistrationToken token) override {
        return inner->remove_NewBrowserVersionAvailable(token);
    }
};

static HRESULT TestCreateEnvironment(PCWSTR browser, PCWSTR, ICoreWebView2EnvironmentOptions *options,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *handler) {
    ++environmentCalls;
    if (failEnvironmentCalls) { --failEnvironmentCalls; return E_FAIL; }
    if (failEnvironmentCallbacks) { --failEnvironmentCallbacks; handler->Invoke(E_FAIL, nullptr); return S_OK; }
    ComPtr<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler> target = handler;
    return CreateCoreWebView2EnvironmentWithOptions(browser, testProfile.c_str(), options,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [target](HRESULT result, ICoreWebView2Environment *environment) -> HRESULT {
                if (FAILED(result) || !environment) return target->Invoke(result, nullptr);
                auto wrapper = Microsoft::WRL::Make<TestEnvironment>();
                wrapper->inner = environment;
                return target->Invoke(result, wrapper.Get());
            }).Get());
}

// Include the production implementation, injecting only the WebView factory. Preincluded
// SDK/STL headers keep test visibility changes confined to the player class.
#define CreateCoreWebView2EnvironmentWithOptions TestCreateEnvironment
#define SHGetFolderPathW TestGetFolderPath
#define private public
#include "../../../desktopMain/native/windows/player_bridge.cpp"
#undef private
#undef CreateCoreWebView2EnvironmentWithOptions
#undef SHGetFolderPathW

static void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}

static void pumpUntil(const std::shared_ptr<WindowsMpvWebPlayer> &player,
    const std::function<bool()> &done, DWORD timeoutMs = 25000) {
    const auto deadline = GetTickCount64() + timeoutMs;
    while (!done() && GetTickCount64() < deadline) {
        MSG message;
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        player->onTimer();
        Sleep(10);
    }
    require(done(), "Timed out waiting for controls recovery");
}

static void checkStateReplay(const std::shared_ptr<WindowsMpvWebPlayer> &player) {
    bool completed = false;
    bool restored = false;
    player->webView->ExecuteScript(L"window.lastTitle",
        Callback<ICoreWebView2ExecuteScriptCompletedHandler>([&](HRESULT result, LPCWSTR json) -> HRESULT {
            restored = SUCCEEDED(result) && json && std::wstring(json) == L"\"episode-two\"";
            completed = true;
            return S_OK;
        }).Get());
    pumpUntil(player, [&] { return completed; });
    require(restored, "Recovered controls did not receive the latest state");
}

static void checkControlsAboveVideo(const std::shared_ptr<WindowsMpvWebPlayer> &player) {
    const HWND videoWindow = FindWindowExW(player->containerHwnd, nullptr, L"mpv", nullptr);
    require(videoWindow != nullptr, "Missing video child in layering regression test");
    // Reproduce the recovered controls being inserted behind an existing video window.
    SetWindowPos(videoWindow, HWND_TOP, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
    require(GetWindow(player->containerHwnd, GW_CHILD) == videoWindow, "Could not reproduce covered controls");
    player->layoutNativeSubviews();
    require(GetWindow(player->containerHwnd, GW_CHILD) != videoWindow &&
        GetWindow(videoWindow, GW_HWNDNEXT) == nullptr, "Video still covers the recovered controls");
}

int wmain(int argc, wchar_t **argv) {
    if (argc != 2) return 2;
    testDataRoot = argv[1];
    CreateDirectoryW((testDataRoot + L"\\NuvioCodeineXO").c_str(), nullptr);
    testProfile = std::wstring(argv[1]) + L"\\profile";
    const auto pagePath = std::wstring(argv[1]) + L"\\controls.html";
    FILE *page = _wfopen(pagePath.c_str(), L"w");
    if (!page) return 2;
    fputs("<script>window.playerControls=s=>window.lastTitle=s.title;window.playerUpdate=()=>{};"
          "chrome.webview.postMessage({type:'controlsReady',value:0});</script>", page);
    fclose(page);
    std::wstring pageUrl = L"file:///" + pagePath;
    std::replace(pageUrl.begin(), pageUrl.end(), L'\\', L'/');
    gModule = GetModuleHandleW(nullptr);
    OleInitialize(nullptr);
    registerWindowClasses();
    WNDCLASSW videoClass = {};
    videoClass.lpfnWndProc = DefWindowProcW;
    videoClass.hInstance = gModule;
    videoClass.lpszClassName = L"mpv";
    RegisterClassW(&videoClass);
    try {
        for (int scenario = 0; scenario < 8; ++scenario) {
            environmentCalls = 0;
            if (scenario == 0) failEnvironmentCalls = 1;
            if (scenario == 1) failEnvironmentCallbacks = 1;
            if (scenario == 2) failControllerCalls = 1;
            if (scenario == 3) failControllerCallbacks = 1;
            if (scenario == 4 || scenario == 7) stallController = true;
            if (scenario == 6) failEnvironmentCalls = 5;
            auto player = std::make_shared<WindowsMpvWebPlayer>();
            player->uiThreadId = GetCurrentThreadId();
            player->hostHwnd = CreateWindowExW(0, L"STATIC", L"", WS_OVERLAPPEDWINDOW,
                0, 0, 640, 480, nullptr, nullptr, gModule, nullptr);
            player->containerHwnd = CreateWindowExW(0, kContainerWindowClass, L"", WS_CHILD,
                0, 0, 640, 480, player->hostHwnd, nullptr, gModule, player.get());
            player->messageHwnd = CreateWindowExW(0, kMessageWindowClass, L"", 0,
                0, 0, 0, 0, HWND_MESSAGE, nullptr, gModule, player.get());
            CreateWindowExW(0, L"mpv", L"", WS_CHILD | WS_VISIBLE,
                0, 0, 640, 480, player->containerHwnd, nullptr, gModule, nullptr);
            player->updateControlsJson("{\"title\":\"episode-two\"}");
            player->startWebView(toUtf8(pageUrl));
            if (scenario == 4 || scenario == 7) {
                pumpUntil(player, [] { return stalledCallback != nullptr; });
                if (scenario == 4) {
                    player->webViewAttemptStartedAt = GetTickCount64() - player->kWebViewStartupTimeoutMs;
                }
            }
            if (scenario == 6) {
                while (player->webViewFailureCount < player->kMaxWebViewFailures) {
                    player->webViewRetryAt = GetTickCount64();
                    player->onTimer();
                }
                player->webViewRetryAt = GetTickCount64();
                player->onTimer();
                require(environmentCalls == 5 && !player->webViewAttemptInFlight &&
                    player->webViewRetryAt == 0, "Permanent failures were not bounded");
            } else if (scenario != 7) {
                pumpUntil(player, [&] { return player->controlsWebReady.load(); });
                require(scenario == 5 || environmentCalls >= 2, "Failure did not recreate the environment");
                checkStateReplay(player);
                checkControlsAboveVideo(player);
                if (scenario == 4) {
                    stalledCallback->Invoke(E_FAIL, nullptr);
                    stalledCallback.Reset();
                    require(player->controlsWebReady.load(), "Stale callback invalidated recovered controls");
                }
                if (scenario == 5) {
                    player->retryWebView("Injected process exit", E_FAIL);
                    pumpUntil(player, [&] { return player->controlsWebReady.load(); });
                    checkStateReplay(player);
                    checkControlsAboveVideo(player);
                }
            }
            const HWND host = player->hostHwnd;
            player->shuttingDown.store(true);
            player->cleanupUiResources();
            if (scenario == 7) {
                stalledCallback->Invoke(E_FAIL, nullptr);
                stalledCallback.Reset();
                require(player->webViewRetryAt == 0, "Shutdown scheduled a controls retry");
            }
            DestroyWindow(host);
            printf("PASS scenario %d\n", scenario);
        }
    } catch (const std::exception &error) {
        fprintf(stderr, "FAIL: %s\n", error.what());
        return 1;
    }
    OleUninitialize();
    return 0;
}
