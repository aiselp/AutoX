import org.gradle.process.ExecSpec

fun ExecSpec.execCommand(command: String) {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("win")) {
        commandLine(System.getenv("ComSpec") ?: "cmd", "/c", command)
    } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
        commandLine("sh", "-c", command)
    } else {
        throw RuntimeException("Unsupported OS: $osName")
    }
}
