import Context = android.Context

declare namespace root {
    type Int = number
    type Float = number

    type Activity = {}

    function exit(): void
    const context: Context
}