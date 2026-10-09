import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('./views/HomeView.vue') },
  { path: '/collection', name: 'collection', component: () => import('./views/CollectionView.vue') },
  { path: '/mine', name: 'mine', component: () => import('./views/MineView.vue') },
  { path: '/bury', name: 'bury', component: () => import('./views/BuryView.vue') },
  { path: '/admin', name: 'admin', component: () => import('./views/AdminView.vue') },
  { path: '/exhibit/:id', name: 'exhibit', component: () => import('./views/ExhibitView.vue') },
  { path: '/exhibit/:id/echo', name: 'echo', component: () => import('./views/EchoView.vue') },
  { path: '/archive', name: 'archive-index', component: () => import('./views/ArchiveIndexView.vue') },
  { path: '/archive/:id', name: 'archive', component: () => import('./views/ArchiveView.vue') },
  { path: '/:pathMatch(.*)*', name: 'missing', component: () => import('./views/MissingView.vue') }
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})
