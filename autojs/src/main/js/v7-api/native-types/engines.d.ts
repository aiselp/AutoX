declare namespace engines {
    interface ScriptEngine {
        id: number
        isDestroyed: boolean
        forceStop()
        cwd(): string
        emit(name: String, ...args: any)
    }
    interface NodeScriptEngine extends ScriptEngine {

    }
    type ExecutionConfig = {
        workingDirectory: string
        arguments: Map<string, any>
    }
    type ScriptExecution = {}

    const selfEngine: any | undefined
    function myEngine(): NodeScriptEngine
    function allEngine(): Set<ScriptEngine>
    function stopAll()
    function stopAllAndToast()
    function createExecutionConfig(): ExecutionConfig
    function execScriptFile(path: String,
        config?: ExecutionConfig | null,
        listener?: ((a: number, ...args) => void) | null
    ): ScriptExecution
    function setupJs(ops: {
        emitCallback: (name: string, ...args: any[]) => void
    })
}

