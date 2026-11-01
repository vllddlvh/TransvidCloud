import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatch } from 'react-router-dom'
import { FolderOpen, Video, Menu, Search, LogOut, Cloud, ChevronRight } from 'lucide-react'
import { Brand } from './Brand'
import { useAuth } from './Auth'

function useIsMobile() {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 800px)').matches)
  useEffect(() => {
    const query = window.matchMedia('(max-width: 800px)')
    const change = (event: MediaQueryListEvent) => setMobile(event.matches)
    query.addEventListener('change', change)
    return () => query.removeEventListener('change', change)
  }, [])
  return mobile
}

export function Layout() {
  const { session, signOut } = useAuth()
  const isMobile = useIsMobile()
  const [drawer, setDrawer] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
  const [search, setSearch] = useState('')
  const location = useLocation()
  const project = useMatch('/projects/:id/videos')
  const shown = isMobile ? drawer : !collapsed
  useEffect(() => { setSearch(''); setDrawer(false) }, [location.pathname])
  return <div className={`workspace ${shown ? '' : 'nav-collapsed'}`}>
    <a className="skip-link" href="#content">Skip to content</a>
    <header className="header">
      <div className="header-brand"><button className="icon-button" aria-label="Toggle navigation" aria-expanded={shown} onClick={() => isMobile ? setDrawer(!drawer) : setCollapsed(!collapsed)}><Menu /></button>
        <Link to="/projects" aria-label="TransVidCloud home"><Brand /></Link>
      </div>
      <div className="search-box"><Search size={18} aria-hidden="true" /><input aria-label="Search this page" placeholder="Search your workspace" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
      <div className="header-account"><span className="account-name">{session?.user.name}</span><span className="avatar" aria-hidden="true">{session?.user.name.slice(0, 1).toUpperCase()}</span>
        <button className="icon-button" aria-label="Sign out" title="Sign out" onClick={signOut}><LogOut size={20} /></button>
      </div>
    </header>
    {drawer && <button className="sidebar-dismiss" aria-label="Close navigation" onClick={() => setDrawer(false)} />}
    <aside className={`sidebar ${drawer ? 'sidebar-open' : ''}`}>
      <div className="workspace-label"><span className="workspace-icon"><Cloud size={20} /></span><div><strong>My workspace</strong><span>Personal workspace</span></div></div>
      <span className="eyebrow nav-caption">LIBRARY</span>
      <nav aria-label="Main navigation">
        <NavLink to="/projects" end><FolderOpen /><span>Projects</span><ChevronRight size={16} /></NavLink>
        {project ? <NavLink to={`/projects/${encodeURIComponent(project.params.id!)}/videos`}><Video /><span>Project videos</span></NavLink>
          : <span className="nav-disabled" title="Open a project to see its videos"><Video /><span>Project videos</span></span>}
      </nav>
      <div className="sidebar-bottom"><Cloud size={24} /><strong>Your content. Organized.</strong><p>A simpler home for your<br />video workflow.</p><span className="small">TRANSVIDCLOUD · WORKSPACE</span></div>
    </aside>
    <main className="main-content" id="content"><Outlet context={{ search }} /></main>
  </div>
}
