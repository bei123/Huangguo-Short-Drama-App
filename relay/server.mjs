/**
 * 黄果线路中转：在有代理/出境网络的服务器上跑，
 * App 先打这里拿 go-home，再本地测速选线，避免直连被墙的主站。
 *
 * 环境变量（可选覆盖）：
 *   PORT / BIND / UPSTREAM / CACHE_TTL_MS / HTTPS_PROXY / HTTP_PROXY
 */
import http from 'node:http'
import { fetch as undiciFetch, ProxyAgent } from 'undici'

/** 默认走局域网代理；可用环境变量覆盖 */
const DEFAULT_PROXY = 'http://192.168.0.51:7897'

const PORT = Number(process.env.PORT || 8787)
const BIND = process.env.BIND || '127.0.0.1'
const UPSTREAM = (process.env.UPSTREAM || 'https://huangguoai.com/api/site/go-home').trim()
const CACHE_TTL_MS = Number(process.env.CACHE_TTL_MS || 5 * 60 * 1000)
const PROXY = (process.env.HTTPS_PROXY || process.env.HTTP_PROXY || DEFAULT_PROXY).trim()

const dispatcher = PROXY.length > 0 ? new ProxyAgent(PROXY) : undefined

let cacheBody = ''
let cacheAt = 0
let cacheStatus = 0

function cors(res) {
	res.setHeader('Access-Control-Allow-Origin', '*')
	res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS')
	res.setHeader('Access-Control-Allow-Headers', 'Accept, Content-Type')
}

function send(res, status, body, contentType = 'application/json; charset=utf-8') {
	cors(res)
	res.writeHead(status, {
		'Content-Type': contentType,
		'Cache-Control': 'public, max-age=60'
	})
	res.end(body)
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
		// routes：国内可用站点；permanent 里保留主站/高速线路，丢掉发布页
		if (Array.isArray(data.routes)) {
			for (const it of data.routes) pushEndpoint(out, seen, it)
		}
		if (Array.isArray(data.permanent)) {
			for (const it of data.permanent) pushEndpoint(out, seen, it)
		}
	} catch (_) {}
	return out
}

async function pullUpstream() {
	const now = Date.now()
	if (cacheBody && now - cacheAt < CACHE_TTL_MS && cacheStatus >= 200 && cacheStatus < 300) {
		return { status: cacheStatus, body: cacheBody, cached: true }
	}
	const headers = {
		Accept: 'application/json',
		Referer: 'https://huangguoai.com/',
		'User-Agent': 'HuangguoRelay/1.0'
	}
	const opts = { method: 'GET', headers, bodyTimeout: 12000, headersTimeout: 12000 }
	if (dispatcher) opts.dispatcher = dispatcher
	const resp = await undiciFetch(UPSTREAM, opts)
	const body = await resp.text()
	cacheStatus = resp.status
	cacheBody = body
	cacheAt = now
	return { status: resp.status, body, cached: false }
}

async function handleGoHome(res) {
	try {
		const { status, body, cached } = await pullUpstream()
		cors(res)
		res.writeHead(status, {
			'Content-Type': 'application/json; charset=utf-8',
			'Cache-Control': 'public, max-age=60',
			'X-HG-Relay-Cache': cached ? 'HIT' : 'MISS',
			'X-HG-Relay-Upstream': UPSTREAM
		})
		res.end(body)
	} catch (e) {
		const msg = e instanceof Error ? e.message : String(e)
		send(res, 502, JSON.stringify({ status: 0, msg: 'upstream fail: ' + msg }))
	}
}

async function handleRoutes(res) {
	try {
		const { status, body } = await pullUpstream()
		if (status < 200 || status >= 300) {
			send(res, status, body)
			return
		}
		const endpoints = extractEndpoints(body)
		send(
			res,
			200,
			JSON.stringify({
				status: 1,
				updated_at: Date.now(),
				count: endpoints.length,
				endpoints
			})
		)
	} catch (e) {
		const msg = e instanceof Error ? e.message : String(e)
		send(res, 502, JSON.stringify({ status: 0, msg: 'upstream fail: ' + msg }))
	}
}

const server = http.createServer(async (req, res) => {
	const url = new URL(req.url || '/', 'http://localhost')
	const path = url.pathname.replace(/\/+$/, '') || '/'

	if (req.method === 'OPTIONS') {
		cors(res)
		res.writeHead(204)
		res.end()
		return
	}

	if (req.method !== 'GET') {
		send(res, 405, JSON.stringify({ status: 0, msg: 'method not allowed' }))
		return
	}

	if (path === '/' || path === '/health') {
		send(
			res,
			200,
			JSON.stringify({
				status: 1,
				service: 'hg-gohome-relay',
				proxy: PROXY.length > 0,
				upstream: UPSTREAM,
				paths: ['/go-home', '/api/site/go-home', '/routes']
			})
		)
		return
	}

	if (path === '/go-home' || path === '/api/site/go-home') {
		await handleGoHome(res)
		return
	}

	if (path === '/routes') {
		await handleRoutes(res)
		return
	}

	send(res, 404, JSON.stringify({ status: 0, msg: 'not found' }))
})

server.listen(PORT, BIND, () => {
	console.log(`[hg-relay] http://${BIND}:${PORT}`)
	console.log(`[hg-relay] upstream=${UPSTREAM}`)
	console.log(`[hg-relay] proxy=${PROXY || '(direct)'}`)
})
