import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ORGANIZATION_TYPES, humanize } from '../constants';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: '',
    organizationName: '',
    organizationType: ORGANIZATION_TYPES[0],
  });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(form);
      navigate('/home');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1>Create account</h1>
        <p className="hint">Registering creates your user and a new organization that you own.</p>
        {error && <div className="error-banner">{error}</div>}
        <label>
          Full name
          <input value={form.fullName} onChange={(e) => update('fullName', e.target.value)} required />
        </label>
        <label>
          Email
          <input type="email" value={form.email} onChange={(e) => update('email', e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" minLength={8} value={form.password} onChange={(e) => update('password', e.target.value)} required />
        </label>
        <label>
          Organization name
          <input value={form.organizationName} onChange={(e) => update('organizationName', e.target.value)} required />
        </label>
        <label>
          Organization type
          <select value={form.organizationType} onChange={(e) => update('organizationType', e.target.value)}>
            {ORGANIZATION_TYPES.map((t) => (
              <option key={t} value={t}>{humanize(t)}</option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create account'}</button>
        <p>Already have an account? <Link to="/login">Log in</Link></p>
      </form>
    </div>
  );
}
