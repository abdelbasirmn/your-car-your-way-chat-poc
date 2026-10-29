import { test, expect, Page } from '@playwright/test';

async function login(
  page: Page,
  email: string,
  password: string
) {
  await page.goto('/');

  await page.getByLabel('Adresse du compte').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);

  const [response] = await Promise.all([
    page.waitForResponse(
      response =>
        new URL(response.url()).pathname === '/api/v1/session' &&
        response.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', {
      name: 'Se connecter',
      exact: true
    }).click()
  ]);

  expect(
    response.status(),
    'La connexion doit retourner HTTP 200'
  ).toBe(200);

  await expect(
    page.getByRole('button', {
      name: 'Se déconnecter',
      exact: true
    })
  ).toBeVisible({ timeout: 15000 });

  // Verify the authenticated identity, not only the presence of a logout button.
  // page.request shares this browser context's cookies.
  const sessionResponse = await page.request.get('/api/v1/session', {
    timeout: 15000
  });

  expect(
    sessionResponse.status(),
    'La session authentifiee doit etre accessible en HTTP 200'
  ).toBe(200);

  const session = await sessionResponse.json();
  expect(
    session.email,
    'La session doit appartenir au compte qui vient de se connecter'
  ).toBe(email);
}

test('Échange privé, reprise SSE, texte inerte et clavier',async({browser})=>{
  const a=await browser.newContext(),s=await browser.newContext(),b=await browser.newContext();
  const alice=await a.newPage(),support=await s.newPage(),bob=await b.newPage();
  try{
    await login(alice,'alice@example.test','Demo-Alice-2026!');
    await alice.getByRole('button',{name:'Nouvelle conversation',exact:true}).click();
    await expect(alice.getByRole('heading',{name:/Conversation /})).toBeVisible();
    const title=await alice.getByRole('heading',{name:/Conversation /}).innerText();
    const shortId=title.split(' ')[1];
    await alice.getByLabel('Votre message',{exact:true}).fill('Bonjour du test navigateur');
    await alice.getByRole('button',{name:'Envoyer le message',exact:true}).click();
    await expect(alice.locator('.history')).toContainText('Bonjour du test navigateur');
    await login(support,'support@example.test','Demo-Support-2026!');
    const waiting=support.locator('.requests li').filter({hasText:'Demande de Alice'}).first();
    // New conversations are listed newest first; take the one just opened.
    await waiting.getByRole('button',{name:'Prendre en charge'}).click();
    await expect(support.getByRole('heading',{name:'Conversation '+shortId,exact:true})).toBeVisible();
    await expect(support.locator('.history')).toContainText('Bonjour du test navigateur');
    await alice.getByRole('button',{name:'Suspendre / reprendre la réception'}).click();
    await support.getByLabel('Votre message',{exact:true}).fill('Réponse pendant la coupure');
    await support.getByRole('button',{name:'Envoyer le message',exact:true}).click();
    await expect(support.locator('.history')).toContainText('Réponse pendant la coupure');
    await alice.getByRole('button',{name:'Suspendre / reprendre la réception'}).click();
    await expect(alice.locator('.history')).toContainText('Réponse pendant la coupure');
    const input=alice.getByLabel('Votre message',{exact:true});
    await input.fill('<script>window.pwned=true</script>');await input.focus();
    await alice.keyboard.press('Tab');await expect(alice.getByRole('button',{name:'Envoyer le message',exact:true})).toBeFocused();
    await alice.keyboard.press('Enter');await expect(alice.locator('.history')).toContainText('<script>window.pwned=true</script>');
    expect(await alice.evaluate(()=>('pwned' in window))).toBe(false);
    await login(bob,'bob@example.test','Demo-Bob-2026!');
    const conversations=await alice.evaluate(async()=>await (await fetch('/api/v1/conversations')).json());
    const target=conversations.find((c:any)=>c.id.startsWith(shortId));
    const denied=await bob.request.get('/api/v1/conversations/'+target.id+'/messages');expect(denied.status()).toBe(404);
    await alice.getByRole('button',{name:'Se déconnecter'}).click();
    await expect(alice.getByRole('button',{name:'Se connecter',exact:true})).toBeVisible();
  }finally{await a.close();await s.close();await b.close();}
});

test('Conserve le même identifiant si la réponse HTTP est perdue',async({page})=>{
  await login(page,'alice@example.test','Demo-Alice-2026!');
  await page.getByRole('button',{name:'Nouvelle conversation',exact:true}).click();
  await expect(page.getByRole('heading',{name:/Conversation /})).toBeVisible();
  let first=true;const keys:string[]=[];
  await page.route('**/api/v1/conversations/*/messages',async route=>{
    if(route.request().method()!=='POST'){await route.continue();return;}
    keys.push(route.request().postDataJSON().clientMessageId);
    if(first){first=false;await route.fetch();await route.abort('failed');}else await route.continue();
  });
  await page.getByLabel('Votre message',{exact:true}).fill('Un seul message malgré la réponse perdue');
  await page.getByRole('button',{name:'Envoyer le message',exact:true}).click();
  await expect(page.getByRole('button',{name:'Réessayer le même envoi',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Réessayer le même envoi',exact:true}).click();
  await expect(page.getByRole('button',{name:'Réessayer le même envoi',exact:true})).not.toBeVisible();
  expect(keys).toHaveLength(2);expect(keys[0]).toBe(keys[1]);
  await expect(page.locator('.history p').filter({hasText:'Un seul message malgré la réponse perdue'})).toHaveCount(1);
});
