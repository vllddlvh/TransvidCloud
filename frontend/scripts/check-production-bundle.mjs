import { existsSync } from 'node:fs'
import { readFile, readdir, stat } from 'node:fs/promises'
import { join } from 'node:path'

const dist = join(process.cwd(), 'dist')
const manifestPath = join(dist, '.vite', 'manifest.json')
if (!existsSync(manifestPath)) throw new Error('missing dist/.vite/manifest.json; run npm run build first')

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
const entries = Object.entries(manifest)
const entry = manifest['index.html']
if (!entry) throw new Error('manifest has no index.html entry')
if (existsSync(join(dist, 'mockServiceWorker.js'))) throw new Error('production build contains mockServiceWorker.js')
if (entries.some(([key]) => /mocks\//.test(key))) throw new Error('production build references src/mocks')

const assetsDir = join(dist, 'assets')
for (const file of (await readdir(assetsDir)).filter((name) => name.endsWith('.js'))) {
  const code = await readFile(join(assetsDir, file), 'utf8')
  if (code.includes('/media/demo-project/')) throw new Error(`fixture URL leaked into ${file}`)
  if (code.includes('setupWorker')) throw new Error(`MSW runtime leaked into ${file}`)
}

const mainSize = (await stat(join(dist, entry.file))).size
if (mainSize > 700_000) throw new Error(`main chunk is ${mainSize} bytes; expected code splitting`)

const hlsEntry = entries
  .map(([, value]) => value)
  .find((value) => value.isDynamicEntry && /hlsplayer/i.test(value.file))
if (!hlsEntry) throw new Error('HlsPlayer is not a dynamic entry')
const hlsSize = (await stat(join(dist, hlsEntry.file))).size
if (hlsSize < 200_000) throw new Error(`HlsPlayer chunk is only ${hlsSize} bytes; hls.js may not be split`)

console.log(`production bundle OK: main ${mainSize} B, HlsPlayer ${hlsSize} B`)
