

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace ui.JSX {
        type gravity_options = 'left' | 'right' | 'top' | 'bottom' | 'center' | 'center_vertical' | 'center_horizontal'
        interface View {
            [key: string]: any
            id?: string
            bg?: string
            w?: 'auto' | '*' | string
            h?: 'auto' | '*' | string
            gravity?: gravity_options
            layout_gravity?: gravity_options
            padding?: string
            visibility?: 'gone' | 'visible' | 'invisible'

        }
        interface Text extends View {
            text?: string
        }
        export interface IntrinsicElements {
            [elemName: string]: View
            vertical: View
            button: View
            horizontal: View
            text: Text
            input: View
            img: View
            frame: View
            checkbox: View
            radio: View
            radiogroup: View
            switch: View
            card: View
            drawer: View
            list: View
            fab: View
        }
    }
}

export { }