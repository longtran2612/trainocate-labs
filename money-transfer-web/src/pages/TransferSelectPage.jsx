import { useNavigate } from 'react-router-dom';

export default function TransferSelectPage() {
  const navigate = useNavigate();

  return (
    <div className="page">
      <div className="page-header">
        <h2>Money Transfer</h2>
      </div>

      <div className="transfer-select-grid">
        <button className="transfer-option" onClick={() => navigate('/transfer/internal')}>
          <div className="transfer-option-icon">&#x1F3E6;</div>
          <h3>Internal Transfer</h3>
          <p>Transfer to another VikkiBank account</p>
        </button>

        <button className="transfer-option" onClick={() => navigate('/transfer/external')}>
          <div className="transfer-option-icon">&#x1F310;</div>
          <h3>External Transfer</h3>
          <p>Transfer to another bank</p>
        </button>
      </div>
    </div>
  );
}
