import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const poolsDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'pools')

interface PoolEntry {
  key: string
  label: string
  path: string
}

interface PoolManifest {
  current?: PoolEntry
  next?: PoolEntry
  previous: PoolEntry[]
}

function buildManifest(): PoolManifest {
  const manifest: PoolManifest = { previous: [] }
  const rootFiles = fs.existsSync(poolsDir)
    ? fs.readdirSync(poolsDir).filter((f) => f.endsWith('.json'))
    : []
  for (const file of rootFiles) {
    const key = file.replace(/\.json$/, '')
    const entry: PoolEntry = { key, label: key, path: file }
    if (key === 'frontier') {
      manifest.current = entry
    } else if (key === 'frontier-next') {
      manifest.next = entry
    }
  }
  const previousDir = path.join(poolsDir, 'previous')
  if (fs.existsSync(previousDir)) {
    for (const file of fs.readdirSync(previousDir).filter((f) => f.endsWith('.json'))) {
      const key = file.replace(/\.json$/, '')
      manifest.previous.push({ key, label: key, path: `previous/${file}` })
    }
  }
  return manifest
}

function poolsPlugin(): Plugin {
  return {
    name: 'card-pool-pools',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = decodeURIComponent((req.url ?? '').split('?')[0])
        if (url === '/pools/manifest.json') {
          res.setHeader('Content-Type', 'application/json')
          res.end(JSON.stringify(buildManifest()))
          return
        }
        if (url.startsWith('/pools/')) {
          const filePath = path.resolve(poolsDir, url.slice('/pools/'.length))
          if (
            filePath.startsWith(poolsDir + path.sep) &&
            fs.existsSync(filePath) &&
            fs.statSync(filePath).isFile()
          ) {
            res.setHeader('Content-Type', 'application/json')
            res.end(fs.readFileSync(filePath))
            return
          }
          res.statusCode = 404
          res.end('Not found')
          return
        }
        next()
      })
    },
    writeBundle(options) {
      const outDir = options.dir ?? 'dist'
      const dest = path.join(outDir, 'pools')
      if (fs.existsSync(dest)) fs.rmSync(dest, { recursive: true })
      fs.cpSync(poolsDir, dest, { recursive: true })
      fs.writeFileSync(path.join(dest, 'manifest.json'), JSON.stringify(buildManifest()))
    },
  }
}

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: (tag) => tag.startsWith('altered-card'),
        },
      },
    }),
    poolsPlugin(),
  ],
  server: {
    fs: {
      allow: ['..'],
    },
  },
})
