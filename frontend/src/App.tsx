import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, RequireAuth } from './components/Auth'
import { NoticeProvider } from './components/Notice'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Projects } from './pages/Projects'
import { Videos } from './pages/Videos'
import { VideoDetail } from './pages/VideoDetail'
import { Embed } from './pages/Embed'
import { UploadTransportContext } from './components/UploadDialog'
import { putFile } from './api/transport'

export default function App({ uploadTransport = putFile }: { uploadTransport?: typeof putFile }) {
  return <AuthProvider><NoticeProvider><UploadTransportContext.Provider value={uploadTransport}>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/embed" element={<Embed />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/projects" element={<Projects />} />
          <Route path="/projects/:id/videos" element={<Videos />} />
          <Route path="/videos/:id" element={<VideoDetail />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/projects" replace />} />
      <Route path="*" element={<main className="empty-state"><h1>Page not found</h1><Link to="/projects">Back to projects</Link></main>} />
    </Routes>
  </UploadTransportContext.Provider></NoticeProvider></AuthProvider>
}
