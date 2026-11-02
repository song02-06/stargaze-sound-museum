import { createRouter, createWebHistory } from 'vue-router'
import { bootstrap, loggedIn } from './store/museum.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
  { path: '/', name: 'home', component: () => import('./views/HomeView.vue') },
  { path: '/collection', name: 'collection', component: () => import('./views/CollectionView.vue') },
  { path: '/mine', name: 'mine', component: () => import('./views/MineView.vue') },
  { path: '/bury', name: 'bury', component: () => import('./views/BuryView.vue') },
  // 编辑同一件投稿：和 /bury 是两件事，所以是两条路由
  {
    path: '/mine/uploads/:no/edit',
    name: 'edit-upload',
    component: () => import('./views/EditUploadView.vue')
  },
  { path: '/admin', name: 'admin', component: () => import('./views/AdminView.vue') },
  { path: '/exhibit/:id', name: 'exhibit', component: () => import('./views/ExhibitView.vue') },
  { path: '/exhibit/:id/echo', name: 'echo', component: () => import('./views/EchoView.vue') },
  { path: '/archive', name: 'archive-index', component: () => import('./views/ArchiveIndexView.vue') },
  { path: '/archive/:id', name: 'archive', component: () => import('./views/ArchiveView.vue') },
  { path: '/:pathMatch(.*)*', name: 'missing', component: () => import('./views/MissingView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const personal = to.path === '/mine' || to.path.startsWith('/mine/') || to.path === '/bury' || to.name === 'echo'
  if (!personal) return
  try { await bootstrap() } catch { return { path: '/login', query: { next: to.fullPath } } }
  if (!loggedIn.value) return { path: '/login', query: { next: to.fullPath } }
})
export default router
