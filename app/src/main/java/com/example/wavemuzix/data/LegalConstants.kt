package com.example.wavemuzix.data

object LegalConstants {
    const val PRIVACY_POLICY = """
🔒 PRIVACY POLICY
Last Updated: August 2026

At Lumino Global and Lumino Studios, we believe in total transparency and user trust. This Privacy Policy details exactly how Wave Muzix handles user permissions, data access, and device security to keep your music streaming secure.

1. Application Sourcing & Third-Party Services
YouTube API Services: Wave Muzix utilizes internal API architecture to query public music data and streaming URLs. By using this app, you agree to the YouTube Terms of Service (https://www.youtube.com/t/terms).
SponsorBlock Integration: The app integrates the SponsorBlock API to identify and skip non-music segments (sponsors, intros) in YouTube-sourced audio. Segment data is fetched anonymously.
Lyrics Providers: Synchronized and plain lyrics are fetched from third-party providers including LRCLIB, Netease, and Alsong. No personal identifiers are shared with these services.
Google Privacy Policy: Project infrastructure complies with Google’s framework. Review it at http://www.google.com/policies/privacy.
Cloud Sync & Firebase: If you opt-in to "Manage Account," your library (favorites/playlists) is securely synced using Google Firebase (Auth, Firestore, Database).
No Third-Party Sale: We do not sell or monetize personal user information with external advertising networks.

2. Device Permissions and Data Usage
Wave Muzix requests specific runtime permissions to enable advanced playback:
Microphone Access: Used for real-time music visualizers. Audio frequency data is processed locally and is NEVER recorded, stored, or transmitted.
Notifications & Foreground Service: Required to maintain a Media3 session for lock-screen controls and background playback stability.
Wake Lock: Used to prevent system sleep during critical background tasks like audio crossfading, prefetching, and downloads.
Storage / Media Access: 
- READ_MEDIA_AUDIO/IMAGES (Android 13+) and READ_EXTERNAL_STORAGE: Used for profile customization and local library management.
- WRITE_EXTERNAL_STORAGE: Used to safely export downloaded audio tracks to your device's music folders.

3. App Feature Optimizations
UI Graphics Rendering: Advanced filters (Haze, Liquid Glass) and AGSL shaders run fully on-device via local GPU rendering.
Local Caching: Room Database stores your favorites, playlist structures, and lyrics cache locally for offline access and performance.
Cloud Backup: If Cloud Sync is enabled, data is backed up to your private Firebase account for multi-device continuity.

4. Communication & Support
If you use the feedback or support features, your email and provided information are processed via our secure mailing infrastructure (SendGrid/Gmail) solely to assist you.
"""

    const val TERMS_AND_CONDITIONS = """
📝 TERMS AND CONDITIONS
Last Updated: August 2026

Welcome to Wave Muzix, developed and operated by Lumino Global and Lumino Studios. Please read these Terms and Conditions carefully before exploring our platform.

1. Description of Service
Wave Muzix provides a high-fidelity music streaming, downloading, and playback platform. We offer a unified repository system pulling from YouTube and utilize SponsorBlock technology to enhance the listening experience by skipping non-music segments. The service is provided free of charge for personal use.

2. Use of App Capabilities
System Resource Access: Users acknowledge that features like real-time liquid glass, responsive crossfading, and prefetching require local processor power and background WakeLock maintenance.
Media Handling & MP3 Export: The download engine (utilizing mp3agic) is designed for personal backup. Users are responsible for adhering to local copyright laws regarding media exported to their device.

3. Intellectual Property Rights
Software Ownership: All custom Android Studio frameworks, specialized UI components (Haze, Liquid Glass), custom AGSL shader nodes, and the Unified Repository architecture are the exclusive property of Lumino Global and Lumino Studios.
Content Sourcing: Audio streams, thumbnail art, and lyrics fetched via third-party APIs belong to their respective creators and hosting platforms.

4. Service Continuity & Updates
Updates are engineered to prevent thread bottlenecks and UI drops. However, Wave Muzix is provided "as-is." We are not liable for carrier data costs or OS-specific rendering bugs (e.g., shader limits on older devices).

5. System Security & Fair Use
Any attempt to reverse-engineer core streaming endpoints or maliciously disrupt the repository network will result in a block. Users agree not to exploit the internal API parser for non-personal use.

6. User Feedback
By submitting feedback or support requests, you grant us the right to use your suggestions to improve the app. We prioritize user privacy and will never share your email address with third parties.
"""
}
