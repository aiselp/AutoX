import { XML } from "@/ui";

export interface DialogBuildProperties {
    title?: string;
    titleColor?: string | number;
    buttonRippleColor?: string | number;
    icon?: string | Autox.Image;
    content?: string;
    contentColor?: string | number;
    contentLineSpacing?: number;
    items?: string[];
    itemsColor?: string | number;
    itemsSelectMode?: 'single' | 'multi' | 'select';
    itemsSelectedIndex?: number | number[];
    positive?: string;
    positiveColor?: string | number;
    negative?: string;
    negativeColor?: string | number;
    neutral?: string;
    neutralColor?: string | number;
    checkBoxPrompt?: string;
    checkBoxChecked?: boolean;
    progress?: {
        max?: number;
        horizontal?: boolean;
        showMinMax?: boolean;
    }
    cancelable?: boolean;
    canceledOnTouchOutside?: boolean;
    inputHint?: string;
    inputPrefill?: string;
    wrapInScrollView?: boolean;
    customView?: string | android.View | XML
}


export interface JsDialogEvents {
    show: (dialog: JsDialog) => void
    dismiss: (dialog: JsDialog) => void
    cancel: (dialog: JsDialog) => void
    positive: (dialog: JsDialog) => void
    negative: (dialog: JsDialog) => void
    neutral: (dialog: JsDialog) => void
    any: (action: "positive" | "negative" | "neutral", dialog: JsDialog) => void
    item_select: (index: number, item: string, dialog: JsDialog,) => void
    single_choice: (index: number, item: string, dialog: JsDialog) => void
    multi_choice: (index: number, items: string[], dialog: JsDialog) => void
    input: (input: string, dialog: JsDialog) => void
    input_change: (text: string, dialog: JsDialog) => void
}

export interface JsDialog {
    on<T extends keyof JsDialogEvents>(event: T, callback: JsDialogEvents[T]): this
    getProgress(): number;
    setProgress(value: number): void;
    getMaxProgress(): number;
    show(): void;
    dismiss(): void;
}