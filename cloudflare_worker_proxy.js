/**
 * Cloudflare Worker 免科学上网双引擎反向代理脚本
 * 适用于：Google Gemini 3 / 2.5 系列 与 OpenAI ChatGPT
 * 特性：
 * 1. 中国大陆三大运营商网络毫秒级直连，免翻墙
 * 2. 完美支持 Server-Sent Events (SSE) 流式传输无缓冲
 * 3. 自动根据请求路径智能转发至 Google 或 OpenAI 官方端点
 * 
 * 部署方法 (仅需 1 分钟)：
 * 1. 访问 https://dash.cloudflare.com/ 注册/登录 (免费账户即可)
 * 2. 点击 "Workers & Pages" -> "Create application" -> "Create Worker"
 * 3. 将本文件全部代码复制并粘贴替换进去，点击 "Save and deploy"
 * 4. 获得分配的 xxx.workers.dev 域名，填入手机 App 的「反代地址」即可！
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 处理 CORS 预检请求
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "*",
        },
      });
    }

    // 智能路由目标端点
    let targetHost = "generativelanguage.googleapis.com";
    if (url.pathname.startsWith("/v1/chat") || url.pathname.startsWith("/v1/models")) {
      targetHost = "api.openai.com";
    } else if (url.pathname.includes("models") || url.pathname.includes("v1beta")) {
      targetHost = "generativelanguage.googleapis.com";
    }

    const targetUrl = new URL(request.url);
    targetUrl.protocol = "https:";
    targetUrl.host = targetHost;
    targetUrl.port = "";

    // 复制请求头并重写 Host
    const newHeaders = new Headers(request.headers);
    newHeaders.set("Host", targetHost);
    newHeaders.delete("cf-connecting-ip");
    newHeaders.delete("x-forwarded-for");

    const modifiedRequest = new Request(targetUrl.toString(), {
      method: request.method,
      headers: newHeaders,
      body: request.body,
      redirect: "follow",
    });

    try {
      const response = await fetch(modifiedRequest);
      const modifiedResponseHeaders = new Headers(response.headers);
      
      // 允许跨域与流式无缓冲传输
      modifiedResponseHeaders.set("Access-Control-Allow-Origin", "*");
      modifiedResponseHeaders.set("Cache-Control", "no-cache, no-transform");
      modifiedResponseHeaders.set("X-Accel-Buffering", "no");

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: modifiedResponseHeaders,
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 502,
        headers: { "Content-Type": "application/json" },
      });
    }
  },
};
