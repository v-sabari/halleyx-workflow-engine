import "./Header.css";

function Header({ toggleSidebar }) {
  return (
    <header className="app-header">

      {/* MOBILE SIDEBAR BUTTON */}
      <button
        className="menu-toggle"
        onClick={toggleSidebar}
      >
        ☰
      </button>

      <h1 className="header-title">
        Workflow Engine
      </h1>

    </header>
  );
}

export default Header;