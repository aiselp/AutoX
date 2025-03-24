import typescript from '@rollup/plugin-typescript';
import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import replace from '@rollup/plugin-replace'
import json from '@rollup/plugin-json';
import { babel } from '@rollup/plugin-babel';
import terser from '@rollup/plugin-terser';
import fs from 'fs'
import path from 'node:path'

let isDev
if (process.env.NODE_ENV === 'production') {
    isDev = false
} else {
    isDev = true
}
const dirs = fs.readdirSync('./src').filter((name) => {
    if (name == 'inline_modules') {
        return false
    }
    return fs.statSync(path.resolve("src", name)).isDirectory()
})

const input = Object.fromEntries(dirs.map(name => [
    path.join(name, 'index'),
    path.resolve("src", name, 'index.ts')
]))
input['init'] = "src/init.ts"

export default {
    input,
    output: {
        dir: "dist",
        format: 'commonjs',
        entryFileNames: "[name].js"
    },
    plugins: [
        typescript({
            outDir: "dist",
        }),
        resolve(), commonjs(), json(),
        babel({
            babelHelpers: 'bundled',
            extensions: ['.js', '.ts'],
            exclude: 'node_modules/**', // 排除 node_modules 目录
            presets: ['@babel/preset-env'] // 使用 Babel 的 env 预设
        }), //terser(),
    ],
    // external: ['lodash']
}