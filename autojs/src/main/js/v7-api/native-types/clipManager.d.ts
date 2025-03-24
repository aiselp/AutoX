
declare namespace clipManager {
    function getClip(): string
    function setClip(text: String)
    function hasClip(): boolean
    function clearClip(): void
    function registerListener(
        onPrimaryClipChangedListener: { onClipChanged: () => void }
    ): void
}