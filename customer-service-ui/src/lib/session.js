// Compatibility shim. Identity now lives in AuthContext (username/password login
// backed by the shared Users table). Pages still call useSession()/docCustomerId()
// as before; this just forwards to the auth context.

import { useAuth } from '../context/AuthContext';

/** The identifier used for document uploads / lookups and capstone-ui search. */
export function docCustomerId(session) {
  return session?.loanCustomerId || session?.email || '';
}

/** { session, update, logout } — session is the authenticated user object. */
export function useSession() {
  const { currentUser, updateUser, logout } = useAuth();
  return { session: currentUser, update: updateUser, logout };
}
