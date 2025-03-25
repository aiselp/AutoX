
namespace Autox {
    interface ShellResult {
        code: number
        result: string
        error: string
    }
    interface Shizuku {
        isShizukuAlive(): boolean
        runRhinoScriptFile(path: string): string
        runRhinoScript(script: string): string
        runShizukuShellCommand(cmd: string): ShellResult
        openAccessibility(): void
    }
    interface Shell2 {
        exec(cmd: string): void
        execAndWaitFor(cmd: string): ShellResult
        exit(): void
        exitAndWaitFor(): void
        setCallback(callback: {
            onOutput: (out: string) => void
            onNewLine: (line: string) => void
        }): void
    }
    interface Shell {
        exec(cmd: string, root: boolean): ShellResult
        createShell(root: boolean): Shell2
    }
    interface Files {
        path(relativePath: string): string
        cwd(): string
        readAssets(path: string, encoding?: string): string
        join(parent: string, ...child: string[]): string
        read(path: string): string
    }

    interface Media {
        scanFile(path: string): void
        playMusic(path: string, volume?: number, looping?: boolean): void
        musicSeekTo(m: number): void
        isMusicPlaying(): boolean
        pauseMusic(): void
        resumeMusic(): void
        getMusicDuration(): number
        getMusicCurrentPosition(): number
        stopMusic(): void
    }
    interface Ui {
        readonly layoutInflater: any
        bindingContext: any
        __proxy__: any
    }
    interface Runtime {
        shizuku: Shizuku
        shell: Shell
        files: Files
        media: Media
        ui: Ui
        evalInContext(script: string, context: Object): any
        getUiHandler: () => any
    }
}