export default function InfoCard({ title, icon, children, className = '' }) {
  return (
    <div className={`info-card ${className}`}>
      <div className="info-card-header">
        <span className="info-card-icon">{icon}</span>
        <h3>{title}</h3>
      </div>
      <div className="info-card-body">{children}</div>
    </div>
  );
}
