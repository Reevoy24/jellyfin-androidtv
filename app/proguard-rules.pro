-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough

# kotlinx.serialization — explicitly keep our @Serializable models' generated serializers and
# Companions so R8 (full mode) can't strip them (belt-and-suspenders over the library's bundled
# consumer rules). Covers ui.jellyseerr and ui.calendar models.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class org.jellyfin.androidtv.**$$serializer { *; }
-keepclassmembers class org.jellyfin.androidtv.** {
    *** Companion;
}
-keepclasseswithmembers class org.jellyfin.androidtv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Fragments are instantiated reflectively by the navigation framework (fragmentDestination<T>()).
-keepclassmembers class org.jellyfin.androidtv.** extends androidx.fragment.app.Fragment {
    public <init>();
}
