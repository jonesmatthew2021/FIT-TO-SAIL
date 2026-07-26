import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ApiError } from './api/client'
import { SessionProvider } from './api/session'
import { Layout, NAV_ITEMS } from './components/Layout'
import { Dashboard } from './screens/Dashboard'
import { NotBuilt } from './screens/NotBuilt'
import { People } from './screens/People'
import { PersonDetail } from './screens/PersonDetail'
import { SwingPlanner } from './screens/SwingPlanner'

/**
 * A 4xx is the server's considered answer — a role check, a scope check, a validation failure —
 * and retrying it just delays the message. Only transient failures are worth a second attempt.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
  },
})

export function App(): React.ReactNode {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Dashboard />} />
              <Route path="planner" element={<SwingPlanner />} />
              <Route path="people" element={<People />} />
              <Route path="people/:personId" element={<PersonDetail />} />
              {NAV_ITEMS.filter((item) => !item.built).map((item) => (
                <Route key={item.to} path={item.to.slice(1)} element={<NotBuilt />} />
              ))}
              <Route path="*" element={<NotBuilt />} />
            </Route>
          </Routes>
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
