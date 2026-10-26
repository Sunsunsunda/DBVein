import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AnalysisPage } from './pages/AnalysisPage'
import { ProjectPage } from './pages/ProjectPage'
import './index.css'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ProjectPage />} />
        <Route path="/projects/:projectId/analysis" element={<AnalysisPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
