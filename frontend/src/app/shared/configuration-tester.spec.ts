import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Catalog } from '../core/catalog.service';
import { ConfigurationTester } from './configuration-tester';

const catalog: Catalog = {
  id: 'c',
  name: 'Laptop',
  readOnly: false,
  revision: 1,
  features: [
    { id: 'gpu', code: 'GPU', name: 'Dedicated GPU' },
    { id: 'charger', code: 'CHARGER', name: 'High-wattage charger' },
  ],
  groups: [],
};

describe('ConfigurationTester', () => {
  it('sends the chosen features and explains why they are not allowed', async () => {
    TestBed.configureTestingModule({
      imports: [ConfigurationTester],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConfigurationTester);
    fixture.componentRef.setInput('catalog', catalog);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLInputElement>('input[type="checkbox"]')?.click();
    element.querySelector('button')?.click();

    const request = http.expectOne('/api/catalogs/c/configurations/check');
    expect(request.request.body).toEqual({ featureIds: ['gpu'] });

    request.flush({
      valid: false,
      missing: [{ message: 'Dedicated GPU requires High-wattage charger' }],
      conflicts: [],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'Dedicated GPU requires High-wattage charger',
    );
    http.verify();
  });
});
