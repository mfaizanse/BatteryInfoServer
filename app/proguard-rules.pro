# Add project specific ProGuard rules here.
# Keep NanoHTTPD if minify is ever enabled
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }
