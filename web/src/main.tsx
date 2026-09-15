import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider, Navigate } from "react-router-dom";
import "./styles.css";
import EventPage from "./pages/EventPage";
import SearchPage from "./pages/SearchPage";

const router = createBrowserRouter([
  { path: "/", element: <Navigate to="/e/demo" replace /> },
  { path: "/e/:eventId", element: <EventPage /> },
  { path: "/e/:eventId/search", element: <SearchPage /> },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);
