// Compatibility shim. Identity now lives in AuthContext (username/password login
// backed by the shared Users table). Pages still call useSession()/docCustomerId()
// as before; this just forwards to the auth context.

import { useAuth } from '../context/AuthContext';

export function docCustomerId(session) {
  if (!session) {
    try {
      const rawUser = localStorage.getItem('csp_user');
      if (rawUser) {
        const u = JSON.parse(rawUser);
        return u.customerId || u.customerServiceId || u.loanCustomerId || u.email || '';
      }
    } catch {}
    return '';
  }
  return session?.customerId || session?.customerServiceId || session?.loanCustomerId || session?.email || '';
}

/** { session, update, logout } — session is the authenticated user object. */
export function useSession() {
  const { currentUser, updateUser, logout } = useAuth();
  return { session: currentUser, update: updateUser, logout };
}
