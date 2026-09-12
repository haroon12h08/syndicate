import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const FEATURES = [
  {
    title: 'Evidence, spatially grounded',
    body: 'Every uploaded document is checksummed, parsed with real PDF text-layer extraction or OCR, and every extracted number is anchored to its exact page and bounding box — not just a file reference.',
  },
  {
    title: 'Zero AI authority',
    body: 'Automated extraction only ever produces a candidate. Nothing becomes a verified fact — or reaches evidence-linked status — without a human explicitly accepting it.',
  },
  {
    title: 'Immutable fact history',
    body: 'Facts are never overwritten. Correcting a value supersedes the old version and creates a new one, so the full history of what was known and when stays intact.',
  },
];

export default function LandingPage() {
  const { user } = useAuth();

  return (
    <div className="landing">
      <header className="landing-topbar">
        <Link to="/" className="landing-brand">
          <img src="/syndicate.png" alt="Syndicate" />
          SYNDICATE
        </Link>
        <div className="landing-topbar-actions">
          {user ? (
            <Link to="/home"><button>Go to dashboard</button></Link>
          ) : (
            <>
              <Link to="/login"><button className="secondary">Log in</button></Link>
              <Link to="/register"><button>Create account</button></Link>
            </>
          )}
        </div>
      </header>

      <section className="landing-hero">
        <div className="landing-hero-copy">
          <div className="eyebrow">Capital / Compliance / Execution</div>
          <h1>Infrastructure for What's Next</h1>
          <p>
            A unified platform to take Indian companies from private to public —
            structured company and transaction data, evidence-grounded facts, and
            a collaborative due-diligence workflow built for NSE and BSE SME IPOs.
          </p>
          <div className="landing-hero-actions">
            {user ? (
              <Link to="/home"><button>Go to dashboard →</button></Link>
            ) : (
              <>
                <Link to="/register"><button>Create New Transaction →</button></Link>
                <Link to="/login"><button className="secondary">Log in</button></Link>
              </>
            )}
          </div>
        </div>
        <div className="landing-skyline">
          <figure className="landing-skyline-half">
            <img src="/nse.png" alt="National Stock Exchange rendered in rupee symbols" />
            <figcaption className="landing-skyline-label">NSE</figcaption>
          </figure>
          <figure className="landing-skyline-half">
            <img src="/bse.png" alt="Bombay Stock Exchange rendered in rupee symbols" />
            <figcaption className="landing-skyline-label">BSE</figcaption>
          </figure>
        </div>
      </section>

      <section className="landing-features">
        <div className="landing-features-grid">
          {FEATURES.map((f) => (
            <div className="landing-feature" key={f.title}>
              <h3>{f.title}</h3>
              <p>{f.body}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="landing-footer">
        <span>Syndicate</span>
        <span>Building trusted markets for a stronger India</span>
      </footer>
    </div>
  );
}
