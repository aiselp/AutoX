export interface Colors {
    [key: string]: any
    BLACK: number;
    DKGRAY: number;
    GRAY: number;
    LTGRAY: number;
    WHITE: number;
    RED: number;
    GREEN: number;
    BLUE: number;
    YELLOW: number;
    CYAN: number;
    MAGENTA: number;
    TRANSPARENT: number;
    parseColor(colorString: string): number;
    toString(color: number): string;
    rgb(red: number, green: number, blue: number): number;
    argb(alpha: number, red: number, green: number, blue: number): number;
    equals(color1: Color, color2: Color): boolean;
}

export type Color = string | number

const colorsExt = {
    alpha: function (color: Color) {
        color = parseColor(color);
        return color >>> 24;
    },
    red: function (color: Color) {
        color = parseColor(color);
        return (color >> 16) & 0xFF;
    },
    green: function (color: Color) {
        color = parseColor(color);
        return (color >> 8) & 0xFF;
    },
    blue: function (color: Color) {
        color = parseColor(color);
        return color & 0xFF;
    },
    isSimilar: function (c1: Color, c2: Color, threshold?: number, algorithm?: string) {
        c1 = parseColor(c1);
        c2 = parseColor(c2);
        threshold = threshold == undefined ? 4 : threshold;
        algorithm = algorithm == undefined ? "diff" : algorithm;
        var colorDetector = getColorDetector(c1, algorithm, threshold);
        return colorDetector.detectsColor(colors.red(c2), colors.green(c2), colors.blue(c2));
    }
}

type ColorsType = Colors & typeof colorsExt
var colors = Object.create(runtime.colors) as ColorsType;
Object.assign(colors, colorsExt);

export function parseColor(color: Color): number {
    if (typeof (color) == 'string') {
        color = colors.parseColor(color) as number;
    }
    return color;
}

export function getColorDetector(color: Color, algorithm: unknown, threshold?: number) {
    switch (algorithm) {
        case "rgb":
            return new com.stardust.autojs.core.image.ColorDetector.RGBDistanceDetector(color, threshold);
        case "equal":
            return new com.stardust.autojs.core.image.ColorDetector.EqualityDetector(color);
        case "diff":
            return new com.stardust.autojs.core.image.ColorDetector.DifferenceDetector(color, threshold);
        case "rgb+":
            return new com.stardust.autojs.core.image.ColorDetector.WeightedRGBDistanceDetector(color, threshold);
        case "hs":
            return new com.stardust.autojs.core.image.ColorDetector.HSDistanceDetector(color, threshold);
    }
    throw new Error("Unknown algorithm: " + algorithm);
}

declare global {
    var colors: ColorsType
}
global.colors = colors;