from django import forms
from django.contrib.auth.models import User
from .models import UserProfile, Post, Comment, Group, CompanyPage, Message, ReportedContent


class UserRegistrationForm(forms.ModelForm):
    first_name = forms.CharField(max_length=50, required=True, widget=forms.TextInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'First Name'
    }))
    last_name = forms.CharField(max_length=50, required=True, widget=forms.TextInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Last Name'
    }))
    username = forms.CharField(max_length=50, required=True, widget=forms.TextInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Username (unique handle)'
    }))
    email = forms.EmailField(required=True, widget=forms.EmailInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Email Address'
    }))
    mobile_number = forms.CharField(max_length=20, required=True, widget=forms.TextInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Mobile Number (e.g. +91 98765 43210)'
    }))
    password = forms.CharField(widget=forms.PasswordInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Password (min. 6 characters)'
    }))
    confirm_password = forms.CharField(widget=forms.PasswordInput(attrs={
        'class': 'form-control form-control-lg', 'placeholder': 'Confirm Password'
    }))

    class Meta:
        model = User
        fields = ['first_name', 'last_name', 'username', 'email', 'password']

    def clean_username(self):
        username = self.cleaned_data.get('username')
        if User.objects.filter(username__iexact=username).exists():
            raise forms.ValidationError("Username already taken. Please choose another.")
        return username

    def clean_email(self):
        email = self.cleaned_data.get('email')
        if User.objects.filter(email__iexact=email).exists():
            raise forms.ValidationError("Email address already registered. Please sign in.")
        return email

    def clean(self):
        cleaned_data = super().clean()
        password = cleaned_data.get('password')
        confirm_password = cleaned_data.get('confirm_password')
        if password and confirm_password and password != confirm_password:
            self.add_error('confirm_password', "Passwords do not match.")
        return cleaned_data


class UserProfileForm(forms.ModelForm):
    first_name = forms.CharField(max_length=50, required=False, widget=forms.TextInput(attrs={'class': 'form-control'}))
    last_name = forms.CharField(max_length=50, required=False, widget=forms.TextInput(attrs={'class': 'form-control'}))

    class Meta:
        model = UserProfile
        fields = [
            'headline', 'profession', 'company_name', 'bio', 'location', 'education',
            'skills', 'interests', 'mobile_number', 'whatsapp_number', 'contact_email',
            'linkedin_url', 'github_url', 'twitter_url', 'website_url',
            'avatar', 'cover_photo', 'is_private'
        ]
        widgets = {
            'headline': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'e.g. Senior Software Architect | AI Researcher'}),
            'profession': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'e.g. Full Stack Engineer'}),
            'company_name': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'e.g. Google / Tech Labs'}),
            'bio': forms.Textarea(attrs={'class': 'form-control', 'rows': 4, 'placeholder': 'Tell the community about your journey, interests and goals...'}),
            'location': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'City, Country (e.g. Bengaluru, India)'}),
            'education': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Degree, University / College'}),
            'skills': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Python, Django, Machine Learning, Spring Boot, React'}),
            'interests': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Cloud Computing, System Design, Open Source'}),
            'mobile_number': forms.TextInput(attrs={'class': 'form-control'}),
            'whatsapp_number': forms.TextInput(attrs={'class': 'form-control'}),
            'contact_email': forms.EmailInput(attrs={'class': 'form-control'}),
            'linkedin_url': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://linkedin.com/in/username'}),
            'github_url': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://github.com/username'}),
            'twitter_url': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://x.com/username'}),
            'website_url': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://yourportfolio.dev'}),
            'is_private': forms.CheckboxInput(attrs={'class': 'form-check-input'}),
        }


class PostForm(forms.ModelForm):
    class Meta:
        model = Post
        fields = ['content', 'image', 'video', 'link_url', 'visibility']
        widgets = {
            'content': forms.Textarea(attrs={
                'class': 'form-control border-0 shadow-none post-textarea',
                'rows': 3,
                'placeholder': "What's on your mind? Share an idea, project update, or discussion topic..."
            }),
            'link_url': forms.URLInput(attrs={'class': 'form-control form-control-sm', 'placeholder': 'Attach link: https://...'}),
            'visibility': forms.Select(attrs={'class': 'form-select form-select-sm rounded-pill'}),
        }


class CommentForm(forms.ModelForm):
    class Meta:
        model = Comment
        fields = ['content']
        widgets = {
            'content': forms.TextInput(attrs={
                'class': 'form-control form-control-sm rounded-pill',
                'placeholder': 'Write a constructive comment...'
            })
        }


class GroupForm(forms.ModelForm):
    class Meta:
        model = Group
        fields = ['name', 'description', 'cover_image', 'is_private']
        widgets = {
            'name': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Community / Group Name'}),
            'description': forms.Textarea(attrs={'class': 'form-control', 'rows': 4, 'placeholder': 'Describe purpose, rules and guidelines for this community'}),
            'is_private': forms.CheckboxInput(attrs={'class': 'form-check-input'}),
        }


class CompanyPageForm(forms.ModelForm):
    class Meta:
        model = CompanyPage
        fields = ['name', 'tagline', 'description', 'industry', 'company_size', 'website', 'location', 'logo', 'cover_image']
        widgets = {
            'name': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Company or Organization Name'}),
            'tagline': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Brief tagline, e.g. Building Next-Gen Search Infrastructure'}),
            'description': forms.Textarea(attrs={'class': 'form-control', 'rows': 4, 'placeholder': 'Overview of products, services and mission'}),
            'industry': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'e.g. Artificial Intelligence / Cloud Computing'}),
            'company_size': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'e.g. 50-200 employees'}),
            'website': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://yourcompany.com'}),
            'location': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Headquarters Location'}),
        }


class MessageForm(forms.ModelForm):
    class Meta:
        model = Message
        fields = ['content', 'attachment']
        widgets = {
            'content': forms.TextInput(attrs={
                'class': 'form-control rounded-pill px-4',
                'placeholder': 'Type a message...'
            })
        }


class ReportForm(forms.ModelForm):
    class Meta:
        model = ReportedContent
        fields = ['reason', 'details']
        widgets = {
            'reason': forms.Select(attrs={'class': 'form-select'}, choices=[
                ('spam', 'Spam or Misleading Content'),
                ('harassment', 'Harassment or Hate Speech'),
                ('inappropriate', 'Inappropriate or Explicit Media'),
                ('fake_account', 'Impersonation or Fake Account'),
                ('intellectual_property', 'Intellectual Property Violation'),
                ('other', 'Other Issue'),
            ]),
            'details': forms.Textarea(attrs={'class': 'form-control', 'rows': 3, 'placeholder': 'Additional context or specifics...'}),
        }

