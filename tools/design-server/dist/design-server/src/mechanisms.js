/** 机制库五要素解析：唯一权威是 references/mechanisms.md（规整格式由测试钉死） */
const ELEMENT_KEYS = ['说明', '代表作', '设计旋钮', '常见坑', '常见组合'];
export function parseMechanismsMd(text) {
    const out = [];
    let zone = '';
    let cur;
    for (const line of text.split(/\r?\n/)) {
        const h3 = /^### ([a-z0-9-]+) · (.+?) \((.+)\)\s*$/.exec(line);
        if (h3) {
            cur = {
                key: h3[1], name: h3[2], nameEn: h3[3], zone,
                elements: { '说明': '', '代表作': '', '设计旋钮': '', '常见坑': '', '常见组合': '' },
            };
            out.push(cur);
            continue;
        }
        const h2 = /^## (?!#)(.+)$/.exec(line);
        if (h2) {
            zone = h2[1].trim();
            continue;
        }
        const el = /^- (说明|代表作|设计旋钮|常见坑|常见组合)：(.*)$/.exec(line);
        if (el && cur)
            cur.elements[el[1]] = el[2].trim();
    }
    if (out.length === 0)
        throw new Error('机制库为空或格式不符（须为 ### key · 中文名 (English)）');
    for (const m of out) {
        const missing = ELEMENT_KEYS.filter(k => !m.elements[k]);
        if (missing.length > 0)
            throw new Error(`机制 ${m.key} 缺少要素: ${missing.join(', ')}`);
    }
    return out;
}
