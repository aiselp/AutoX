
declare namespace dialogs {
    type ComposeElement = any
    type TSecureTpye = 'SecureOn' | 'SecureOff' | 'Inherit'

    type DialogOps = {
        dismissOnBackPress: boolean
        dismissOnClickOutside: boolean
        securePolicy?: TSecureTpye
        onDismiss: () => void
    }
    type AppDialogBuilder = {
        dismiss(): void
    }
    function showDialog(element: ComposeElement, ops: DialogOps | null): AppDialogBuilder
}