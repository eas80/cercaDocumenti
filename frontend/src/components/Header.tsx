export default function Header() {
  return (
    <header className="app-header">
      <div className="app-header-inner">
        <span className="app-header-icon" aria-hidden="true">
          📄
        </span>
        <div>
          <h1>Documenti</h1>
          <p className="app-header-subtitle">Ricerca, inserimento, modifica e download</p>
        </div>
      </div>
    </header>
  );
}
