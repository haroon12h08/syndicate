import { createContext, useContext, useMemo, useState } from 'react';

const BreadcrumbContext = createContext(null);

/**
 * Pages publish their own trail because only they know the entity names behind the ids in the
 * URL — deriving "Company / Transaction / Workstream" from the path alone would show UUIDs.
 */
export function BreadcrumbProvider({ children }) {
  const [trail, setTrail] = useState([]);
  const value = useMemo(() => ({ trail, setTrail }), [trail]);
  return <BreadcrumbContext.Provider value={value}>{children}</BreadcrumbContext.Provider>;
}

export function useBreadcrumbs() {
  return useContext(BreadcrumbContext) || { trail: [], setTrail: () => {} };
}
