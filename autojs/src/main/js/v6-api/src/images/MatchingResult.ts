

type Point = any
var comparators: any = {
    "left": (l: Point, r: Point) => l.point.x - r.point.x,
    "top": (l: Point, r: Point) => l.point.y - r.point.y,
    "right": (l: Point, r: Point) => r.point.x - l.point.x,
    "bottom": (l: Point, r: Point) => r.point.y - l.point.y
}

class MatchingResult {
    matches: any[]
    __points__: any

    constructor(list: any) {
        if (Array.isArray(list)) {
            this.matches = list;
        } else {
            this.matches = runtime.bridges.toArray(list);
        }
    }
    get points() {
        if (typeof (this.__points__) == 'undefined') {
            this.__points__ = this.matches.map(m => m.point);
        }
        return this.__points__;
    }

    first() {
        if (this.matches.length == 0) {
            return null;
        }
        return this.matches[0];
    }
    last() {
        if (this.matches.length == 0) {
            return null;
        }
        return this.matches[this.matches.length - 1];
    }
    findMax(cmp: (a: any, b: any) => number) {
        if (this.matches.length == 0) {
            return null;
        }
        var target = this.matches[0];
        this.matches.forEach(m => {
            if (cmp(target, m) > 0) {
                target = m;
            }
        });
        return target;
    }
    leftmost() {
        return this.findMax(comparators.left);
    }
    topmost() {
        return this.findMax(comparators.top);
    }
    rightmost() {
        return this.findMax(comparators.right);
    }
    bottommost() {
        return this.findMax(comparators.bottom);
    }
    worst() {
        return this.findMax((l, r) => l.similarity - r.similarity);
    }
    best() {
        return this.findMax((l, r) => r.similarity - l.similarity);
    }
    sortBy(cmp: any) {
        var comparatorFn: any = null;
        if (typeof (cmp) == 'string') {
            cmp.split("-").forEach(direction => {
                var buildInFn = comparators[direction];
                if (!buildInFn) {
                    throw new Error("unknown direction '" + direction + "' in '" + cmp + "'");
                }
                (function (fn) {
                    if (comparatorFn == null) {
                        comparatorFn = fn;
                    } else {
                        comparatorFn = (function (comparatorFn, fn) {
                            return function (l: unknown, r: unknown) {
                                var cmpValue = comparatorFn(l, r);
                                if (cmpValue == 0) {
                                    return fn(l, r);
                                }
                                return cmpValue;
                            }
                        })(comparatorFn, fn);
                    }
                })(buildInFn);
            });
        } else {
            comparatorFn = cmp;
        }
        var clone = this.matches.slice();
        clone.sort(comparatorFn);
        return new MatchingResult(clone);
    }
}


export { MatchingResult };
