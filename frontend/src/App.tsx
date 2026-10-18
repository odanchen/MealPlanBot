import { JoinListPage } from "./pages/JoinListPage";
import { ShoppingArchivePage } from "./pages/ShoppingArchivePage";
import { ShoppingCataloguePage } from "./pages/ShoppingCataloguePage";
import { ShoppingListPage } from "./pages/ShoppingListPage";
import { AppShell } from "./components/AppShell";
import { RecipeDirectoryPage } from "./pages/RecipeDirectoryPage";
import { RecipePage } from "./pages/RecipePage";
import { UserSchedulesPage } from "./pages/UserSchedulesPage";
import { UserSchedulePage } from "./pages/UserSchedulePage";
export function App() {
  const path = window.location.pathname;
  let page;
  let protectedPage = false;
  const archive = /^\/lists\/([^/]+)\/archive$/.exec(path);
  const list = /^\/lists\/([^/]+)$/.exec(path);
  const recipe = /^\/recipes\/([^/]+)$/.exec(path);
  const schedule = /^\/schedules\/([^/]+)$/.exec(path);
  if (path === "/") page = <RecipeDirectoryPage />;
  else if (recipe) page = <RecipePage id={decodeURIComponent(recipe[1])} />;
  else if (path === "/schedules") {
    page = <UserSchedulesPage />;
    protectedPage = true;
  } else if (schedule) {
    page = <UserSchedulePage id={decodeURIComponent(schedule[1])} />;
    protectedPage = true;
  } else if (path === "/lists") {
    page = <ShoppingCataloguePage />;
    protectedPage = true;
  } else if (list) {
    page = <ShoppingListPage id={decodeURIComponent(list[1])} />;
    protectedPage = true;
  } else if (archive) {
    page = <ShoppingArchivePage id={decodeURIComponent(archive[1])} />;
    protectedPage = true;
  } else if (path === "/join") {
    page = <JoinListPage />;
    protectedPage = true;
  } else if (path === "/signin-error")
    page = (
      <>
        <h2>Sign-in was not completed</h2>
        <a href="/schedules">Try again</a>
      </>
    );
  else page = <h2>Page not found</h2>;
  return <AppShell protectedPage={protectedPage}>{page}</AppShell>;
}
