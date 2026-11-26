/**
 * Cloudflare Worker 版中转（边缘节点直连主站，一般无需自备代理）。
 * 部署：npx wrangler deploy
 *
 * /go-home          透传官方 JSON，但 permanent 去掉发布页/中转页
 * /api/site/go-home 同上
 * /routes           精简可用站点列表
 */
const UPSTREAM = 'https://huangguoai.com/api/site/go-home'

export default {
	async fetch(request) {
		const url = new URL(request.url)
		const path = url.pathname.replace(/\/+$/, '') || '/'

		if (request.method === 'OPTIONS') {
			return new Response(null, {
				status: 204,
				headers: corsHeaders()
			})
		}

		if (path === '/' || path === '/health') {
			return json({
				status: 1,
				service: 'hg-gohome-relay-cf',
				paths: ['/go-home', '/api/site/go-home', '/routes']
			})
		}

		if (path === '/go-home' || path === '/api/site/go-home' || path === '/routes') {
			try {
				const upstream = await fetch(UPSTREAM, {
					headers: {
						Accept: 'application/json',
						Referer: 'https://huangguoai.com/',
						'User-Agent': 'HuangguoRelayCF/1.0'
					}
				})
				const body = await upstream.text()
				if (upstream.status < 200 || upstream.status >= 300) {
					return new Response(body, {
						status: upstream.status,
						headers: {
							'Content-Type': 'application/json; charset=utf-8',
							...corsHeaders(),
							'Cache-Control': 'public, max-age=60'
						}
					})
				}

				if (path === '/routes') {
					const endpoints = extractEndpoints(body)
					return json({
						status: 1,
						updated_at: Date.now(),
						count: endpoints.length,
						endpoints
					})
				}

				const filtered = filterGoHomeBody(body)
				return new Response(filtered, {
					status: 200,
					headers: {
						'Content-Type': 'application/json; charset=utf-8',
						...corsHeaders(),
						'Cache-Control': 'public, max-age=60'
					}
				})
			} catch (e) {
				return json({ status: 0, msg: 'upstream fail: ' + String(e) }, 502)
			}
		}

		return json({ status: 0, msg: 'not found' }, 404)
	}
}

function corsHeaders() {
	return {
		'Access-Control-Allow-Origin': '*',
		'Access-Control-Allow-Methods': 'GET, OPTIONS',
		'Access-Control-Allow-Headers': 'Accept, Content-Type'
	}
}

function json(obj, status = 200) {
	return new Response(JSON.stringify(obj), {
		status,
		headers: {
			'Content-Type': 'application/json; charset=utf-8',
			...corsHeaders()
		}
	})
}

/** 发布页/中转页：给人收藏用，不是站点 API */
function isPublishPage(it, host) {
	const label = String(it?.label || '')
	const note = String(it?.note || '')
	const text = label + note
	if (/发布页|中转页/.test(text)) return true
	if (host.endsWith('.pages.dev')) return true
	if (host === 'huangguoai.ai' || host === 'www.huangguoai.ai') return true
	if (/^hgai\d+\.com$/i.test(host)) return true
	return false
}

function pushEndpoint(out, seen, it) {
	let d = (it?.domain || it?.url || '').trim()
	if (!d) return
	if (!/^https?:\/\//i.test(d)) d = 'https://' + d
	d = d.replace(/\/+$/, '')
	const host = d.replace(/^https?:\/\//i, '').split('/')[0].toLowerCase()
	if (!host || seen.has(host)) return
	if (isPublishPage(it, host)) return
	seen.add(host)
	out.push(d)
}

function extractEndpoints(raw) {
	const out = []
	const seen = new Set()
	try {
		const obj = JSON.parse(raw)
		const data = obj?.data
		if (!data) return out
		if (Array.isArray(data.routes)) {
			for (const it of data.routes) pushEndpoint(out, seen, it)
		}
		if (Array.isArray(data.permanent)) {
			for (const it of data.permanent) pushEndpoint(out, seen, it)
		}
	} catch (_) {}
	return out
}

/** 透传结构，但 permanent 去掉发布页 */
function filterGoHomeBody(raw) {
	try {
		const obj = JSON.parse(raw)
		const data = obj?.data
		if (!data || !Array.isArray(data.permanent)) {
			return raw
		}
		data.permanent = data.permanent.filter((it) => {
			let d = (it?.domain || it?.url || '').trim()
			if (!d) return false
			if (!/^https?:\/\//i.test(d)) d = 'https://' + d
			const host = d.replace(/^https?:\/\//i, '').split('/')[0].toLowerCase()
			return !isPublishPage(it, host)
		})
		return JSON.stringify(obj)
	} catch (_) {
		return raw
	}
}
