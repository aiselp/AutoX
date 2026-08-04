
export type gravity_options = 'left' | 'right' | 'top' | 'bottom' | 'center' | 'center_vertical' | 'center_horizontal'
export interface View {
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
export interface Text extends View {
  text?: string
}
export interface El {
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
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ui.JSX {
    export type IntrinsicElements = El
  }
}

export const h: () => any = (function () {
  return eval(`
            function h(tag) {
  let attrs = {};
  let children = [];
  if (typeof arguments[1] === "object") {
    attrs = arguments[1]||{};
    children = Array.prototype.slice.call(arguments, 2);
  } else {
    children = Array.prototype.slice.call(arguments, 1);
  }
  // 创建 XML 标签
  let xmlElement = new XML('<' + tag + ' />');
  // 设置属性
  for (let [key, value] of Object.entries(attrs)) {
    xmlElement.@[key] = value;
  }

  // 添加子元素
  children.forEach((child) => {
    if (typeof child === "string") {
      xmlElement.appendChild(child); // 处理文本节点
    } else {
      xmlElement.appendChild(child); // 处理 XML 对象
    }
  });
  return xmlElement;
}
            `)
})()