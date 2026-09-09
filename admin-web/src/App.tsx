import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ApiError } from './api/client'
import { SessionProvider } from './api/session'
import { Layout, NAV_ITEMS } from './components/Layout'
import { Admin } from './screens/Admin'
import { Business } from './screens/Business'
import { Administration } from './screens/Administration'
import { Company } from './screens/Company'
import { CrewRequests } from './screens/CrewRequests'
import { Dashboard } from './screens/Dashboard'
import { Evidence } from './screens/Evidence'
import { Exceptions } from './screens/Exceptions'
import { Matrix } from './screens/Matrix'
import { NotBuilt } from './screens/NotBuilt'
import { Notifications } from './screens/Notifications'
import { People } from './screens/People'
import { PersonDetail } from './screens/PersonDetail'
import { Register } from './screens/Register'
import { RegisterNew } from './screens/RegisterNew'
import { SwingPlanner } from './screens/SwingPlanner'
import { SwingShiftCompliance } from './screens/SwingShiftCompliance'

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
              <Route path="company" element={<Company />} />
              <Route path="business" element={<Business />} />
              <Route path="admin" element={<Admin />} />
              <Route path="planner" element={<SwingPlanner />} />
              <Route path="swing-compliance" element={<SwingShiftCompliance />} />
              <Route path="people" element={<People />} />
              <Route path="people/:personId" element={<PersonDetail />} />
              <Route path="exceptions" element={<Exceptions />} />
              <Route path="crew-requests" element={<CrewRequests />} />
              <Route path="register" element={<Register />} />
              <Route path="register/new" element={<RegisterNew />} />
              {/* ADM-4 is one screen with the list beside the detail, so a record id selects rather
                  than replaces. Router ranking puts the static `new` above this. */}
              <Route path="register/:recordId" element={<Register />} />
              <Route path="matrix" element={<Matrix />} />
              <Route path="notifications" element={<Notifications />} />
              <Route path="evidence" element={<Evidence />} />
              <Route path="evidence/:publicId" element={<Evidence />} />
              <Route path="administration" element={<Administration />} />
              {/* Every §6 module is now routed. The mapping stays so that adding an unbuilt module
                  to NAV_ITEMS routes it to the honest scope page rather than to a 404. */}
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
