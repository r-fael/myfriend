# Ferramenta chamada via `adb shell app_process` para ativar o mostrador.
# Ninguém referencia ela no app, então o R8 apagaria sem esta regra.
-keep class com.myfriend.SetWatchFaceTool {
    public static void main(java.lang.String[]);
}

-allowaccessmodification
-repackageclasses
