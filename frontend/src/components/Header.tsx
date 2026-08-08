interface HeaderProps {
  onLogout: () => void;
}

export default function Header({ onLogout }: HeaderProps) {
  return (
    <header className="app-header">
      <div className="app-header-inner">
        <span className="app-header-icon" aria-hidden="true">
          📄
        </span>
        <div className="app-header-titles">
          <h1>Documenti</h1>
          <p className="app-header-subtitle">Ricerca, inserimento, modifica e download</p>
        </div>
        <button type="button" className="btn btn-ghost" onClick={onLogout}>
          Esci
        </button>
      </div>
    </header>
  );
}
