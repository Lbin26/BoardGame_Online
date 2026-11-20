/** G6 启动横幅：dev 指向 vite 5173，prod 指向本进程实际端口；PUBLIC_URL 可覆盖 */
export function startupBanner(opts) {
    const env = opts.env ?? process.env;
    const url = env.PUBLIC_URL ?? (opts.serveUi ? `http://localhost:${opts.port}/design` : 'http://localhost:5173/design');
    return `桌游设计助手工作台 → ${url}`;
}
