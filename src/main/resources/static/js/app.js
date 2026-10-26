document.querySelectorAll('[data-table-search]').forEach(input=>input.addEventListener('input',()=>{const table=document.getElementById(input.dataset.tableSearch);const query=input.value.toLowerCase();table.querySelectorAll('tbody tr').forEach(row=>row.hidden=!row.innerText.toLowerCase().includes(query));}));
const confirmDialog=document.querySelector('#action-confirm');
if(confirmDialog){
 let pendingForm=null;
 const confirmMessage=confirmDialog.querySelector('[data-confirm-message]');
 document.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',event=>{
  if(form.dataset.confirmed==='true'){delete form.dataset.confirmed;return;}
  event.preventDefault();
  pendingForm=form;
  confirmMessage.textContent=form.dataset.confirm||'Continue with this action?';
  confirmDialog.showModal();
 }));
 confirmDialog.querySelector('[data-confirm-cancel]').addEventListener('click',()=>{pendingForm=null;confirmDialog.close();});
 confirmDialog.querySelector('[data-confirm-continue]').addEventListener('click',()=>{
  const form=pendingForm; pendingForm=null; confirmDialog.close();
  if(form){form.dataset.confirmed='true';form.requestSubmit();}
 });
 confirmDialog.addEventListener('click',event=>{if(event.target===confirmDialog){pendingForm=null;confirmDialog.close();}});
}

const accountRole=document.querySelector('#role');
if(accountRole){
 const orgField=document.querySelector('#org-field');
 const donorField=document.querySelector('#blood-field');
 const orgInput=document.querySelector('#organization');
 const donorInput=document.querySelector('#bloodGroup');
 const updateRoleFields=()=>{
  const isHospital=accountRole.value==='HOSPITAL';
  orgField.classList.toggle('is-hidden',!isHospital);
  donorField.classList.toggle('is-hidden',isHospital);
  orgInput.required=isHospital;
  orgInput.disabled=!isHospital;
  donorInput.required=!isHospital;
  donorInput.disabled=isHospital;
 };
 accountRole.addEventListener('change',updateRoleFields);
 updateRoleFields();
}

document.querySelectorAll('[data-password-toggle]').forEach(button=>button.addEventListener('click',()=>{
 const input=document.getElementById(button.dataset.passwordToggle);
 const reveal=input.type==='password';
 input.type=reveal?'text':'password';
 button.textContent=reveal?'Hide':'Show';
 button.setAttribute('aria-label',reveal?'Hide password':'Show password');
 button.setAttribute('aria-pressed',String(reveal));
 input.focus();
}));
