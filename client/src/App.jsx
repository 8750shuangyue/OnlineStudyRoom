import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import Layout from './components/Layout.jsx'
import { useAuth } from './auth.jsx'

const LoginPage = lazy(() => import('./pages/LoginPage.jsx'))
const RegisterPage = lazy(() => import('./pages/RegisterPage.jsx'))
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'))
const RoomsPage = lazy(() => import('./pages/RoomsPage.jsx'))
const FriendsPage = lazy(() => import('./pages/FriendsPage.jsx'))
const AchievementsPage = lazy(() => import('./pages/AchievementsPage.jsx'))
const TasksPage = lazy(() => import('./pages/TasksPage.jsx'))
const StatsPage = lazy(() => import('./pages/StatsPage.jsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.jsx'))
const ProfilePage = lazy(() => import('./pages/ProfilePage.jsx'))
const FlashcardsPage = lazy(() => import('./pages/FlashcardsPage.jsx'))
const ChatPage = lazy(() => import('./pages/ChatPage.jsx'))
const RoomPage = lazy(() => import('./pages/RoomPage.jsx'))
const RagPage = lazy(() => import('./pages/RagPage.jsx'))
const MistakePage = lazy(() => import('./pages/MistakePage.jsx'))
const MessageCenterPage = lazy(() => import('./pages/MessageCenterPage.jsx'))
const NotesPage = lazy(() => import('./pages/NotesPage.jsx'))

function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  if (loading) {
    return <div className="center">加载中...</div>
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}

export default function App() {
  return (
    <Suspense fallback={<div className="center">加载中...</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="rooms" element={<RoomsPage />} />
          <Route path="rooms/:id" element={<RoomPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="friends" element={<FriendsPage />} />
          <Route path="messages" element={<MessageCenterPage />} />
          <Route path="achievements" element={<AchievementsPage />} />
          <Route path="tasks" element={<TasksPage />} />
          <Route path="notes" element={<NotesPage />} />
          <Route path="rag" element={<RagPage />} />
          <Route path="mistakes" element={<MistakePage />} />
          <Route path="stats" element={<StatsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="users/:username" element={<ProfilePage />} />
          <Route path="cards" element={<FlashcardsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
