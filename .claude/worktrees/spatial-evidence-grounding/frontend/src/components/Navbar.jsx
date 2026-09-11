import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <nav className="navbar">
      <Link to="/companies" className="navbar-brand">Syndicate</Link>
      <div className="navbar-links">
        <span className="navbar-user">{user.fullName}</span>
        <button onClick={logout} className="link-button">Log out</button>
      </div>
    </nav>
  );
}
