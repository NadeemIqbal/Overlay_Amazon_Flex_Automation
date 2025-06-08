# Service Overlay App

This Android application provides an overlay service with accessibility features, designed to automate certain UI interactions. It is especially useful for automating repetitive tasks in other apps, such as clicking specific screen areas based on user-configurable percentages.

---

**Disclaimer & Usage Note**

- This tool is made specifically for the Amazon Flex app to overcome the tedious manual process of refreshing and grabbing jobs.
- **I am not responsible if your account is suspended, blocked, or if you experience any other harm as a result of using this tool. Use at your own risk.**
- After setting up the permissions, only click the play button when you are inside the Amazon Flex "Offers" screen. This is a simple automation tool and should only be run on that **Offers** screen.
- **Stop the service when you do not want to use this tool or when you want to use other apps.**

---

## Features
- **Overlay Service:** Runs as a foreground service with a floating button overlay.
- **Accessibility Service:** Automates UI interactions using Android's AccessibilityService.
- **Configurable Click Position:**
  - Users can set both the width and height percentages for automated clicks via the app UI.
  - These settings are persisted and used for precise click automation.
- **Automated Job Flow:**
  - Detects specific UI elements (e.g., job offers) and performs actions like clicking or refreshing.
- **Permission Handling:**
  - Guides users to enable required permissions (overlay and accessibility).

## How to Use
1. **Install the App** on your Android device.
2. **Grant Overlay Permission:**
   - The app will prompt you to allow it to draw over other apps.
3. **Enable Accessibility Service:**
   - Follow the in-app instructions to enable the accessibility service for this app.
4. **Configure Click Percentages:**
   - In the main screen, set your desired Height and Width percentages for automated clicks.
   - Tap "Save" for each setting.
5. **Start the Overlay Service:**
   - The floating overlay button will appear. The service will now automate clicks based on your settings.

## Example
- If you set Height to `95` and Width to `75`, the automated click will occur at 75% of the screen width and 95% of the screen height.

## Security Notice
- The app **cannot interact with system UI elements** such as the on-screen keyboard due to Android security restrictions.
- All automated actions are limited to app UIs and cannot access or manipulate sensitive system components.

## Development
- **Language:** Kotlin
- **Minimum SDK:** (specify your minSdkVersion)
- **Main Files:**
  - `MainActivity.kt`: UI and settings
  - `OverlayAccessibilityService.kt`: Accessibility automation
  - `OverlayService.kt`: Overlay floating button

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0). It must always remain public and must attribute the original author: Nadeem Iqbal. See the [LICENSE](LICENSE) file for details. 